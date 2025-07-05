import { Trend, Counter } from 'k6/metrics';
import { createEvent } from './createEvent.js'
import { reserveSeats } from './reserveSeats.js'

const reservationTime = new Trend('reservation_time', true);
const reservationCounter = new Counter('reservation_completed')

export const options = {
  discardResponseBodies: true,
  scenarios: {
    stress: {
      executor: 'ramping-arrival-rate',
      startRate: 5000,

      timeUnit: '1s',

      preAllocatedVUs: 10000,

      stages: [
        { target: 5000, duration: '5s' },

        { target: 6000, duration: '5s' },

        { target: 10000, duration: '1m' },

        { target: 5000, duration: '5s' },
      ],

      gracefulStop: '10s',
    } 
  },
};

const hostPort = __ENV.HOST_PORT ? `${__ENV.HOST_PORT}` : "localhost:8080"
const baseURL = `http://${hostPort}`
const numOfAreas = parseInt(__ENV.NUM_OF_AREAS ? `${__ENV.NUM_OF_AREAS}` : "1")

export function setup(){
  const event = createEvent(baseURL, numOfAreas)
  return event
}

export default function (event) {
  reserveSeats(baseURL, event, reservationTime, reservationCounter)
}
