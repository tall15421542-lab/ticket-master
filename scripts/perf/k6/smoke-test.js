import { Trend, Counter } from 'k6/metrics';
import { createEvent } from './createEvent.js'
import { reserveSeats } from './reserveSeats.js'

const reservationTime = new Trend('reservation_time', true);
const reservationCounter = new Counter('reservation_completed')

export const options = {
  discardResponseBodies: true,
  iterations: 20,
  vus: 1,
};

const hostPort = __ENV.HOST_PORT ? `${__ENV.HOST_PORT}` : "localhost:8080"
const baseURL = `http://${hostPort}`
const numOfAreas = parseInt(__ENV.NUM_OF_AREAS ? `${__ENV.NUM_OF_AREAS}` : "20")

export default function () {
  const event = createEvent(baseURL, numOfAreas)
  reserveSeats(baseURL, event, reservationTime, reservationCounter)
}
