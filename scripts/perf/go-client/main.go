package main

import (
	"bytes"
	"cmp"
	"context"
	"crypto/tls"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"math"
	"math/rand"
	"net"
	"net/http"
	"os"
	"runtime"
	"runtime/pprof"
	"runtime/trace"
	"slices"
	"strconv"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/hashicorp/go-retryablehttp"
	"github.com/tcnksm/go-httpstat"
	"github.com/urfave/cli/v3"
	"go.uber.org/zap"
	"golang.org/x/net/http2"
)

type SugaredLeveledLogger struct {
	logger *zap.SugaredLogger
}

func (s SugaredLeveledLogger) Error(msg string, keysAndValues ...interface{}) {
	s.logger.Errorw(msg, keysAndValues...)
}

func (s SugaredLeveledLogger) Info(msg string, keysAndValues ...interface{}) {
	s.logger.Infow(msg, keysAndValues...)
}

func (s SugaredLeveledLogger) Debug(msg string, keysAndValues ...interface{}) {
	s.logger.Debugw(msg, keysAndValues...)
}

func (s SugaredLeveledLogger) Warn(msg string, keysAndValues ...interface{}) {
	s.logger.Warnw(msg, keysAndValues...)
}

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

type Event struct {
	EventName string `json:"eventName"`
	Artist    string `json:"artist"`
	Areas     []Area `json:"areas"`
}

type Area struct {
	AreaId   string `json:"areaId"`
	RowCount int    `json:"rowCount"`
	ColCount int    `json:"colCount"`
}

var clients []*http.Client
var eventId = "event-" + uuid.NewString()
var logger *zap.SugaredLogger

func getClient() *http.Client {
	return clients[rand.Int()%len(clients)]
}

func createEvent(host string, numOfAreas int) (*Event, error) {
	url := fmt.Sprintf("http://%s/v1/event", host)
	event := Event{
		EventName: eventId,
		Artist:    "go-load-test",
	}

	for areaIdx := 0; areaIdx < numOfAreas; areaIdx = areaIdx + 1 {
		event.Areas = append(event.Areas, Area{
			AreaId:   strconv.Itoa(areaIdx),
			RowCount: 20,
			ColCount: 20,
		})
	}

	jsonData, err := json.Marshal(event)
	if err != nil {
		return nil, err
	}

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(jsonData))
	if err != nil {
		return nil, fmt.Errorf("Error creating request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")

	resp, err := getClient().Do(req)
	if err != nil {
		return nil, fmt.Errorf("Error sending requests: %w", err)
	}
	defer resp.Body.Close()

	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("error reading response body: %w", err)
	}

	var createdEvent Event
	if err := json.Unmarshal(body, &createdEvent); err != nil {
		return nil, fmt.Errorf("error unmarshaling JSON: %w", err)
	}
	return &createdEvent, nil
}

func createReservation(host string, createReservationReq CreateReservation) (string, *httpstat.Result, error) {
	url := fmt.Sprintf("http://%s/v1/event/%s/reservation", host, eventId)

	jsonData, err := json.Marshal(createReservationReq)
	if err != nil {
		return "", nil, fmt.Errorf("Error marshaling JSON: %w", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	var result httpstat.Result
	ctx = httpstat.WithHTTPStat(ctx, &result)

	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(jsonData))
	if err != nil {
		return "", nil, fmt.Errorf("Error creating request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")

	resp, err := getClient().Do(req)
	if err != nil {
		return "", &result, fmt.Errorf("Error sending requests: %w", err)
	}
	defer resp.Body.Close()

	postBody, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return "", &result, fmt.Errorf("error reading response body: %w", err)
	}
	result.End(time.Now())

	reservationId := string(postBody)
	return reservationId, &result, nil
}

func getReservation(host string, reservationId string) (*Reservation, *httpstat.Result, error) {
	url := fmt.Sprintf("http://%s/v1/reservation/%s", host, reservationId)
	ctx, cancel := context.WithTimeout(context.Background(), 300*time.Second)
	defer cancel()

	var result httpstat.Result
	ctx = httpstat.WithHTTPStat(ctx, &result)

	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return nil, nil, err
	}

	resp, err := getClient().Do(req)
	if err != nil {
		return nil, nil, fmt.Errorf("Error sending request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, &result, fmt.Errorf("%d, %s", resp.StatusCode, http.StatusText(resp.StatusCode))
	}

	getBody, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return nil, &result, fmt.Errorf("error reading response body: %w", err)
	}

	result.End(time.Now())

	var reservation Reservation
	if err := json.Unmarshal(getBody, &reservation); err != nil {
		return nil, &result, fmt.Errorf("error unmarshaling JSON: %w", err)
	}
	return &reservation, &result, nil
}

// pretouch to create the connection from the client to the host.
func healthCheck(client *http.Client, host string) error {
	url := fmt.Sprintf("http://%s/v1/health_check", host)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return err
	}

	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("Error sending requests: %w", err)
	}
	defer resp.Body.Close()
	io.Copy(io.Discard, resp.Body)
	return nil
}

func initHttpClients(enableHttp2 bool, numOfClients int, host string) {
	for i := 0; i < numOfClients; i = i + 1 {
		retryClient := retryablehttp.NewClient()
		retryClient.RetryMax = 20
		retryClient.Logger = SugaredLeveledLogger{logger: logger}

		if enableHttp2 {
			var protocols http.Protocols
			protocols.SetUnencryptedHTTP2(true)
			httpClient := &http.Client{
				Transport: &http2.Transport{
					AllowHTTP: true,
					DialTLS: func(network, addr string, cfg *tls.Config) (net.Conn, error) {
						return net.Dial(network, addr)
					},
				},
			}
			retryClient.HTTPClient = httpClient
		}

		client := retryClient.StandardClient() // *http.Client
		err := healthCheck(client, host)
		if err != nil {
			logger.Fatalf("host %s is not healthy: %w", host, err)
		}

		clients = append(clients, client)
	}
}

type Result struct {
	reservation *Reservation
	err         error
	postStats   *httpstat.Result
	getStats    *httpstat.Result
	latency     time.Duration
}

func createConcurrentRequests(host string, event *Event, numOfRequests int, resultChan chan<- Result, timeOfSleep time.Duration) {
	var wg sync.WaitGroup

	testStartTime := time.Now()
	for i := 0; i < numOfRequests; i = i + 1 {
		wg.Add(1)
		go func() {
			defer wg.Done()

			areaIdx := rand.Int() % len(event.Areas)
			req := CreateReservation{
				UserID:     "user123",
				EventID:    eventId,
				AreaID:     event.Areas[areaIdx].AreaId,
				NumOfSeats: rand.Int()%4 + 1,
				Type:       "RANDOM",
			}

			startTime := time.Now()
			reservationId, postStats, err := createReservation(host, req)
			if err != nil {
				resultChan <- Result{
					reservation: nil,
					err:         err,
					postStats:   postStats,
					getStats:    nil,
					latency:     time.Since(startTime),
				}
				return
			}

			time.Sleep(timeOfSleep)

			reservation, getStats, err := getReservation(host, reservationId)
			if err != nil {
				resultChan <- Result{
					reservation: nil,
					err:         err,
					postStats:   postStats,
					getStats:    getStats,
					latency:     time.Since(startTime),
				}
				return
			}

			resultChan <- Result{
				reservation: reservation,
				err:         nil,
				postStats:   postStats,
				getStats:    getStats,
				latency:     time.Since(startTime),
			}
		}()
	}
	wg.Wait()
	logger.Infof("The test is completed in {} second", time.Since(testStartTime).Seconds())
}

func reportResults(numOfRequests int, resultChan <-chan Result) {
	reservedSeats := make(map[string]map[Seat]bool)
	successReservations := 0
	failedReservations := 0
	errResults := 0

	var reservationStats []Result
	var reservationLatency []time.Duration

	for i := 0; i < numOfRequests; i = i + 1 {
		result := <-resultChan
		if result.err != nil {
			errResults = errResults + 1
			logger.Error(result.err)
			continue
		}
		reservationStats = append(reservationStats, result)
		reservationLatency = append(reservationLatency, result.latency)
		reservation := result.reservation
		logger.Debugln(reservation)

		areaID := reservation.AreaID
		if reservedSeats[areaID] == nil {
			reservedSeats[areaID] = make(map[Seat]bool)
		}

		if reservation.State == "RESERVED" {
			successReservations = successReservations + 1
			for _, seat := range reservation.Seats {
				if reservedSeats[areaID][seat] == true {
					log.Fatalf("seat (%d, %d) is already reserved", seat.Row, seat.Col)
				}
				reservedSeats[areaID][seat] = true
			}
		} else {
			failedReservations = failedReservations + 1
		}
	}

	reportResponseTimeStats(reservationStats, reservationLatency)
	logger.Infoln("successful reservations:", successReservations)
	logger.Infoln("failed reservations:", failedReservations)
	logger.Infoln("err results:", errResults)

	totalReservedSeats := 0
	for areaID := range reservedSeats {
		logger.Infoln("area: ", areaID, "- num of reserved seats:", len(reservedSeats[areaID]))
		totalReservedSeats = totalReservedSeats + len(reservedSeats[areaID])
	}
	logger.Infoln("Total reserved Seats: ", totalReservedSeats)
}

func reportResponseTimeStats(reservationStats []Result, reservationLatency []time.Duration) {
	slices.SortFunc(reservationStats, func(r1 Result, r2 Result) int {
		return cmp.Compare(getResponseTime(r1), getResponseTime(r2))
	})

	slices.SortFunc(reservationLatency, func(t1 time.Duration, t2 time.Duration) int {
		return cmp.Compare(t1.Milliseconds(), t2.Milliseconds())
	})

	logger.Infoln(getPercentileResult(reservationStats, reservationLatency, 50))
	logger.Infoln(getPercentileResult(reservationStats, reservationLatency, 95))
	logger.Infoln(getPercentileResult(reservationStats, reservationLatency, 99))
}

func getResponseTime(result Result) int64 {
	var time time.Duration
	if result.postStats != nil {
		time = time + result.postStats.ServerProcessing
	}

	if result.getStats != nil {
		time = time + result.getStats.ServerProcessing
	}
	return time.Milliseconds()
}

func getPercentileResult(reservationStats []Result, reservationLatency []time.Duration, percentileInt int) string {
	if len(reservationStats) == 0 {
		return "0 percentile is not support"
	}

	percentile := float64(percentileInt) / 100.

	numOfRequests := len(reservationStats)
	percentileIdx := float64(numOfRequests-1) * percentile

	lowIdx := int(math.Floor(percentileIdx))
	highIdx := int(math.Ceil(percentileIdx))

	if lowIdx == highIdx {
		processingTime := float64(getResponseTime(reservationStats[lowIdx]))
		latency := float64(reservationLatency[lowIdx].Milliseconds())
		return fmt.Sprintf("P%d: Processing Time %f ms, Latency %f ms", percentileInt, processingTime, latency)
	}

	responseTime1 := getResponseTime(reservationStats[lowIdx])
	responseTime2 := getResponseTime(reservationStats[highIdx])

	latency1 := reservationLatency[lowIdx].Milliseconds()
	latency2 := reservationLatency[highIdx].Milliseconds()

	processingTime := float64(responseTime1)*(float64(highIdx)-percentileIdx) + float64(responseTime2)*(percentileIdx-float64(lowIdx))
	latency := float64(latency1)*(float64(highIdx)-percentileIdx) + float64(latency2)*(percentileIdx-float64(lowIdx))

	return fmt.Sprintf("P%d: Processing Time %f ms, Latency %f ms", percentileInt, processingTime, latency)
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
			&cli.IntFlag{
				Name:    "reqs",
				Value:   20,
				Usage:   "Number of concurrent requests",
				Aliases: []string{"n"},
			},
			&cli.IntFlag{
				Name:    "numOfAreas",
				Value:   1,
				Usage:   "Number of Areas",
				Aliases: []string{"a"},
			},
			&cli.IntFlag{
				Name:    "numOfClients",
				Value:   1,
				Usage:   "Number of clients",
				Aliases: []string{"c"},
			},
			&cli.StringFlag{
				Name:    "sleep",
				Value:   "0s",
				Usage:   "second of sleep between post and get",
				Aliases: []string{"t"},
				Action: func(ctx context.Context, cmd *cli.Command, v string) error {
					_, err := time.ParseDuration(v)
					return err
				},
			},
			&cli.StringFlag{
				Name:    "env",
				Value:   "dev",
				Usage:   "environment",
				Aliases: []string{"e"},
				Action: func(ctx context.Context, cmd *cli.Command, v string) error {
					if v != "dev" && v != "prod" {
						return errors.New(fmt.Sprintf("env flag should be one of 'dev' or 'prod', got %s", v))
					}
					return nil
				},
			},

			&cli.StringFlag{
				Name:    "cpuprofile",
				Value:   "",
				Usage:   "write cpu profile to `file`",
				Aliases: []string{"cpu"},
			},
			&cli.StringFlag{
				Name:    "memprofile",
				Value:   "",
				Usage:   "write memory profile to `file`",
				Aliases: []string{"mem"},
			},
			&cli.StringFlag{
				Name:    "blockprofile",
				Value:   "",
				Usage:   "write block profile to `file`",
				Aliases: []string{"block"},
			},
			&cli.StringFlag{
				Name:    "lockprofile",
				Value:   "",
				Usage:   "write lock profile to `file`",
				Aliases: []string{"lock"},
			},
			&cli.StringFlag{
				Name:    "traceprofile",
				Value:   "",
				Usage:   "write trace profile to `file`",
				Aliases: []string{"trace"},
			},
			&cli.BoolFlag{
				Name:  "http2",
				Value: false,
				Usage: "Enable http2",
			},
		},
		Action: func(ctx context.Context, cmd *cli.Command) error {
			host := cmd.String("host")
			numOfRequests := int(cmd.Int("reqs"))
			enableHttp2 := cmd.Bool("http2")
			timeOfSleep, _ := time.ParseDuration(cmd.String("sleep"))
			numOfAreas := int(cmd.Int("numOfAreas"))
			numOfClients := int(cmd.Int("numOfClients"))
			env := cmd.String("env")
			cpuprofile := cmd.String("cpuprofile")
			memprofile := cmd.String("memprofile")
			blockprofile := cmd.String("blockprofile")
			lockprofile := cmd.String("lockprofile")
			traceprofile := cmd.String("traceprofile")

			if blockprofile != "" {
				runtime.SetBlockProfileRate(1)
			}

			if lockprofile != "" {
				runtime.SetMutexProfileFraction(1)
			}

			if cpuprofile != "" {
				f, err := os.Create(cpuprofile)
				if err != nil {
					log.Fatal("could not create CPU profile: ", err)
				}
				defer f.Close() // error handling omitted for example
				if err := pprof.StartCPUProfile(f); err != nil {
					log.Fatal("could not start CPU profile: ", err)
				}
				defer pprof.StopCPUProfile()
			}

			if traceprofile != "" {
				f, err := os.Create(traceprofile)
				if err != nil {
					log.Fatal("could not create Trace profile: ", err)
				}
				defer f.Close() // error handling omitted for example
				if err := trace.Start(f); err != nil {
					log.Fatalf("profile: could not start trace: %v", err)
				}
				defer trace.Stop()
			}
			var zapLogger *zap.Logger
			if env == "dev" {
				zapLogger, _ = zap.NewDevelopment()
			} else {
				zapLogger, _ = zap.NewProduction()
			}

			defer zapLogger.Sync()
			logger = zapLogger.Sugar()

			initHttpClients(enableHttp2, numOfClients, host)
			resultChan := make(chan Result, numOfRequests)

			event, err := createEvent(host, numOfAreas)
			if err != nil {
				log.Fatal(err)
			}

			logger.Debugln(event)

			logger.Infof("Waiting event %s ready for 2 seconds\n", event.EventName)
			time.Sleep(2 * time.Second)
			logger.Infoln("Completed")

			createConcurrentRequests(host, event, numOfRequests, resultChan, timeOfSleep)
			reportResults(numOfRequests, resultChan)

			if memprofile != "" {
				f, err := os.Create(memprofile)
				if err != nil {
					log.Fatal("could not create memory profile: ", err)
				}
				defer f.Close()
				runtime.GC()
				if err := pprof.Lookup("allocs").WriteTo(f, 0); err != nil {
					log.Fatal("could not write memory profile: ", err)
				}
			}
			if blockprofile != "" {
				f, err := os.Create(blockprofile)
				if err != nil {
					log.Fatal("could not create blockory profile: ", err)
				}
				defer f.Close() // error handling omitted for example
				runtime.GC()
				if err := pprof.Lookup("block").WriteTo(f, 0); err != nil {
					log.Fatal("could not write blockory profile: ", err)
				}
			}
			if lockprofile != "" {
				f, err := os.Create(lockprofile)
				if err != nil {
					log.Fatal("could not create lockory profile: ", err)
				}
				defer f.Close() // error handling omitted for example
				runtime.GC()
				if err := pprof.Lookup("mutex").WriteTo(f, 0); err != nil {
					log.Fatal("could not write lockory profile: ", err)
				}
			}
			return nil
		},
	}

	if err := cmd.Run(context.Background(), os.Args); err != nil {
		log.Fatal(err)
	}
}
