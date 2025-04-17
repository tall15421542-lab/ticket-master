import http from 'k6/http';
import { check, group } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import exec from 'k6/execution';

export function reserveSeats(baseURL, event, reservationTime, reservationCounter){
  const eventId = event.eventName
  const numOfAreas = event.areas.length
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
