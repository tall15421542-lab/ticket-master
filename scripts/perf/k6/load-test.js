import http from 'k6/http';
import { check, group } from 'k6';
import { uuidv4, randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { Trend } from 'k6/metrics';
import exec from 'k6/execution';

const baseURL = "http://35.206.204.178"

export function setup(){
    const eventId = "event-" + uuidv4();
    const payload = JSON.stringify({
        eventName: `${eventId}`,
        artist: "k6 test",
        areas: [
            {areaId: "A", price: 1800, rowCount: 20, colCount: 30},
            {areaId: "B", price: 1000, rowCount: 20, colCount: 30}
        ]
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const url = `${baseURL}/v1/event`
    const res = http.post(url, payload, params);
    const isValid = check(res, {
        'status is 200': (r) => r.status === 200,
    });

    if (!isValid) {
        throw new Error(`Setup failed: Expected status 200 but got ${res.status}`);
    }

    return eventId
}

export const options = {
  discardResponseBodies: true,
  scenarios: {
    spike: {
      executor: 'constant-arrival-rate',

      // How long the test lasts
      duration: '1s',

      // How many iterations per timeUnit
      rate: 10000,

      // Start `rate` iterations per second
      timeUnit: '1s',

      // Pre-allocate 2 VUs before starting the test
      preAllocatedVUs: 20000,

      // Spin up a maximum of 50 VUs to sustain the defined
      // constant arrival rate.
      gracefulStop: '10s',
    },
  },
};

const reservationTime = new Trend('reservation_time', true);

export default function (eventId) {
    group('reserve seats', function(){
        const payload = JSON.stringify({
            userId: "tall154215",
            eventId: `${eventId}`,
            areaId: "A",
            numOfSeats: randomIntBetween(1, 4),
            type: "RANDOM"
        })

        const params = {
            headers: {
                'Content-Type': 'application/json',
            },
            responseType: "text",
        };
        const reserve_seats_url = `${baseURL}/v1/event/${eventId}/reservation`
        const reserve_seats_res = http.post(reserve_seats_url, payload, params)
        check(reserve_seats_res, {
            'status is 200': (r) => r.status === 200,
        });
        console.log(reserve_seats_res.body)
        
        const reservationId = reserve_seats_res.body
        const get_reservation_url = `${baseURL}/v1/reservation/${reservationId}`
        const get_reservation_res = http.get(get_reservation_url, params)
        check(get_reservation_res, {
            'status is 200': (r) => r.status === 200,
        })

        const reservation = JSON.parse(get_reservation_res.body)
        console.log(reservation.state, reservation.seats, reservation.failedReason)
        reservationTime.add(reserve_seats_res.timings.duration + get_reservation_res.timings.duration, [`${exec.vu.tags}`])
    })
}
