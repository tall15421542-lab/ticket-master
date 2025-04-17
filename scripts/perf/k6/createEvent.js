import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import http from 'k6/http';
import { check } from 'k6';

export function createEvent(baseURL, numOfAreas){
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

