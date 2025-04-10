import http from 'k6/http';
import { check, group } from 'k6';
import { uuidv4, randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { Trend, Counter } from 'k6/metrics';
import exec from 'k6/execution';

const hostPort = __ENV.HOST_PORT ? `${__ENV.HOST_PORT}` : "localhost:8080"
const baseURL = `http://${hostPort}`

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
    stress: {
      executor: 'ramping-arrival-rate',
      startRate: 1000,

      timeUnit: '1s',

      preAllocatedVUs: 1000,

      stages: [
        { target: 1000, duration: '5s' },

        { target: 1500, duration: '5s' },

        { target: 2000, duration: '1m' },

        { target: 1000, duration: '5s' },
      ],

      gracefulStop: '10s',
    },
  },
};

const reservationTime = new Trend('reservation_time', true);
const reservationCounter = new Counter('reservation_completed')

export default function (eventId) {
    group('reserve seats', function(){
        const payload = JSON.stringify({
            userId: "tall154215",
            eventId: `${eventId}`,
            areaId: "A",
            numOfSeats: randomIntBetween(1, 4),
            type: "RANDOM"
        })

        const postParams = {
            headers: {
                'Content-Type': 'application/json',
            },
            responseType: "text",
        };

        const reserve_seats_url = `${baseURL}/v1/event/${eventId}/reservation`
        const reserve_seats_res = http.post(reserve_seats_url, payload, postParams)
        check(reserve_seats_res, {
            'status is 200': (r) => r.status === 200,
        });

        if(reserve_seats_res.status != 200) {
            return;
        }
        
        const reservationId = reserve_seats_res.body
        const get_reservation_url = `${baseURL}/v1/reservation/${reservationId}`
        const getParams = {
            headers: {
                'Content-Type': 'application/json',
            },
        }
        const get_reservation_res = http.get(http.url`${get_reservation_url}`, getParams)
        check(get_reservation_res, {
            'status is 200': (r) => r.status === 200,
        })

        reservationTime.add(reserve_seats_res.timings.duration + get_reservation_res.timings.duration, [`${exec.vu.tags}`])
        if(get_reservation_res.status === 200){
            reservationCounter.add(1)
        }
    })
}
