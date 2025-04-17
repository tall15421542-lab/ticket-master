import { Trend, Counter } from 'k6/metrics';
import { createEvent } from './createEvent.js'
import { reserveSeats } from './reserveSeats.js'

const reservationTime = new Trend('reservation_time', true);
const reservationCounter = new Counter('reservation_completed')

export const options = {
  discardResponseBodies: true,
  iterations: 100,
  vus: 10,
};

const hostPort = __ENV.HOST_PORT ? `${__ENV.HOST_PORT}` : "localhost:8080"
const baseURL = `http://${hostPort}`
const numOfAreas = parseInt(__ENV.NUM_OF_AREAS ? `${__ENV.NUM_OF_AREAS}` : "20")

export function setup(){
  const event = createEvent(baseURL, numOfAreas)
  return event
}

export default function (event) {
  reserveSeats(baseURL, event, reservationTime, reservationCounter)
}
