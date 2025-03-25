package main

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"math/rand"
	"net"
	"net/http"
	"os"
	"sync"
	"time"

	"github.com/hashicorp/go-retryablehttp"
	"github.com/urfave/cli/v3"
	"golang.org/x/net/http2"
)

type CreateReservation struct {
	UserID     string `json:"userId"`
	EventID    string `json:"eventId"`
	AreaID     string `json:"areaId"`
	NumOfSeats int    `json:"numOfSeats"`
	Seats      []Seat `json:"seats"`
	Type       string `json:"type"`
}

type Seat struct {
	Row int `json:"row"`
	Col int `json:"col"`
}

type Reservation struct {
	ReservationID string `json:"reservationId"`
	UserID        string `json:"userId"`
	EventID       string `json:"eventId"`
	AreaID        string `json:"areaId"`
	NumOfSeats    int    `json:"numOfSeats"`
	NumOfSeat     int    `json:"numOfSeat"`
	Type          string `json:"type"`
	Seats         []Seat `json:"seats"`
	State         string `json:"state"`
	FailedReason  string `json:"failedReason"`
}

var client *http.Client

func createReservation(host string, createReservationReq CreateReservation) (string, error) {
	url := fmt.Sprintf("http://%s/v1/event/mock-event-id/reservation", host)

	jsonData, err := json.Marshal(createReservationReq)
	if err != nil {
		return "", fmt.Errorf("Error marshaling JSON: %w", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(jsonData))
	if err != nil {
		return "", fmt.Errorf("Error creating request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")

	// HTTP client
	resp, err := client.Do(req)
	if err != nil {
		return "", fmt.Errorf("Error sending requests: %w", err)
	}
	defer resp.Body.Close()

	postBody, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("error reading response body: %w", err)
	}

	reservationId := string(postBody)
	return reservationId, nil
}

func getReservation(host string, reservationId string) (*Reservation, error) {
	url := fmt.Sprintf("http://%s/v1/reservation/%s", host, reservationId)
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)

	if err != nil {
		return nil, err
	}

	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("Error sending request: %w", err)
	}
	defer resp.Body.Close()

	getBody, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("error reading response body: %w", err)
	}

	var reservation Reservation
	if err := json.Unmarshal(getBody, &reservation); err != nil {
		return nil, fmt.Errorf("error unmarshaling JSON: %w", err)
	}
	return &reservation, nil
}

func initHttpClient(enableHttp2 bool) *http.Client {
	retryClient := retryablehttp.NewClient()
	retryClient.RetryMax = 5

	client = retryClient.StandardClient() // *http.Client

	if enableHttp2 {
		var protocols http.Protocols
		protocols.SetUnencryptedHTTP2(true)
		client.Transport = &http2.Transport{
			AllowHTTP: true,
			DialTLS: func(network, addr string, cfg *tls.Config) (net.Conn, error) {
				return net.Dial(network, addr)
			},
		}
	}

	return client
}

type Result struct {
	reservation *Reservation
	err         error
	elapsed     time.Duration
}

func createConcurrentRequests(host string, eventId string, numOfRequests int, resultChan chan<- Result) {
	var wg sync.WaitGroup

	for i := 0; i < numOfRequests; i = i + 1 {
		wg.Add(1)
		go func() {
			defer wg.Done()

			req := CreateReservation{
				UserID:     "user123",
				EventID:    eventId,
				AreaID:     "A",
				NumOfSeats: rand.Int()%4 + 1,
				Type:       "RANDOM",
			}

			begin := time.Now()
			reservationId, err := createReservation(host, req)
			elapsed := time.Since(begin)
			if err != nil {
				resultChan <- Result{
					reservation: nil,
					err:         err,
					elapsed:     elapsed,
				}
				return
			}

			reservation, err := getReservation(host, reservationId)
			elapsed = time.Since(begin)
			if err != nil {
				resultChan <- Result{
					reservation: nil,
					err:         err,
					elapsed:     elapsed,
				}
				return
			}

			resultChan <- Result{
				reservation: reservation,
				err:         nil,
				elapsed:     elapsed,
			}
		}()
	}
	wg.Wait()
}

func reportResults(numOfRequests int, resultChan <-chan Result) {
	reservedSeats := make(map[Seat]bool)
	successReservations := 0
	failedReservations := 0
	errResults := 0
	for i := 0; i < numOfRequests; i = i + 1 {
		result := <-resultChan
		if result.err != nil {
			errResults = errResults + 1
			fmt.Println(i, result.elapsed, result.err)
			continue
		}

		reservation := result.reservation
		if reservation.State == "RESERVED" {
			successReservations = successReservations + 1
			for _, seat := range reservation.Seats {
				if reservedSeats[seat] == true {
					log.Fatalf("seat (%d, %d) is already reserved", seat.Row, seat.Col)
				}
				reservedSeats[seat] = true
			}
		} else {
			failedReservations = failedReservations + 1
		}

		fmt.Println(i, result.elapsed, result.reservation)
	}

	fmt.Println("successful reservations:", successReservations)
	fmt.Println("failed reservations:", failedReservations)
	fmt.Println("err results:", errResults)
	fmt.Println("num of reserved seats", len(reservedSeats))
}

func main() {
	cmd := &cli.Command{
		UseShortOptionHandling: true,
		Flags: []cli.Flag{
			&cli.StringFlag{
				Name:  "host",
				Value: "localhost:8080",
				Usage: "ticket service host",
			},
			&cli.StringFlag{
				Name:    "event",
				Value:   "mock-event-id-b",
				Usage:   "event id",
				Aliases: []string{"e"},
			},
			&cli.IntFlag{
				Name:    "reqs",
				Value:   20,
				Usage:   "Number of concurrent requests",
				Aliases: []string{"n"},
			},
			&cli.BoolFlag{
				Name:  "http2",
				Value: true,
				Usage: "Enable http2",
			},
		},
		Action: func(ctx context.Context, cmd *cli.Command) error {
			host := cmd.String("host")
			eventId := cmd.String("event")
			numOfRequests := int(cmd.Int("reqs"))
			enableHttp2 := cmd.Bool("http2")

			initHttpClient(enableHttp2)
			resultChan := make(chan Result, numOfRequests)

			createConcurrentRequests(host, eventId, numOfRequests, resultChan)
			reportResults(numOfRequests, resultChan)
			return nil
		},
	}

	if err := cmd.Run(context.Background(), os.Args); err != nil {
		log.Fatal(err)
	}
}
