import http from 'k6/http';
import { check, group } from 'k6';
import { uuidv4, randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { Trend, Counter } from 'k6/metrics';
import exec from 'k6/execution';

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

const hostPort = __ENV.HOST_PORT ? `${__ENV.HOST_PORT}` : "localhost:8080"
const baseURL = `http://${hostPort}`
const numOfAreas = parseInt(__ENV.NUM_OF_AREAS ? `${__ENV.NUM_OF_AREAS}` : "1")

export function setup(){
    const eventId = "event-" + uuidv4();
    const event = {
        eventName: `${eventId}`,
        artist: "k6 test",
        areas: []
    };

    for(let areaIdx = 0 ; areaIdx < numOfAreas ; ++areaIdx){
        event.areas.push({
            areaId: areaIdx.toString(),
            rowCount: 20,
            colCount: 20,
        })
    }
    
    const payload = JSON.stringify(event)

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

    return event
}

const reservationTime = new Trend('reservation_time', true);
const reservationCounter = new Counter('reservation_completed')

export default function (event) {
    const eventId = event.eventName
    group('reserve seats', function(){
        const areaIdx = Math.floor(Math.random() * numOfAreas);
        const payload = JSON.stringify({
            userId: "tall154215",
            eventId: `${eventId}`,
            areaId: event.areas[areaIdx].areaId,
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

        if(get_reservation_res.status === 200){
            reservationTime.add(reserve_seats_res.timings.duration + get_reservation_res.timings.duration, [`${exec.vu.tags}`])
            reservationCounter.add(1)
        }
    })
}
