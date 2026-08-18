'use strict';

/**
 * The pretend route-optimization brain.
 *
 * A real ROS would solve a travelling-salesman problem over live traffic data.
 * This does a greedy "always drive to the nearest stop you have not visited
 * yet" pass, which is a genuine (if naive) optimization and produces sensible
 * looking output for the demo.
 */

/** Where every route starts. */
const DEPOT = {
  label: 'SwiftLogistics Depot, Colombo',
  lat: 6.9271,
  lng: 79.8612,
};

const AVERAGE_SPEED_KMH = 30;
const MINUTES_PER_DROP_OFF = 5;
const EARTH_RADIUS_KM = 6371;

const toRadians = (degrees) => (degrees * Math.PI) / 180;

/** Straight-line distance between two points on the globe, in kilometres. */
function distanceKm(from, to) {
  const deltaLat = toRadians(to.lat - from.lat);
  const deltaLng = toRadians(to.lng - from.lng);

  const a =
    Math.sin(deltaLat / 2) ** 2 +
    Math.cos(toRadians(from.lat)) * Math.cos(toRadians(to.lat)) * Math.sin(deltaLng / 2) ** 2;

  return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
}

/**
 * Invents stable coordinates for an address that came without any.
 *
 * The same address always produces the same point, so repeated calls for one
 * order return the same route. It is nonsense geographically, but it keeps the
 * adapter simple: it can send just a street address.
 */
function coordinatesForAddress(address) {
  let hash = 0;
  for (const character of String(address)) {
    hash = (hash * 31 + character.charCodeAt(0)) % 100000;
  }

  // Scatter the points within roughly 0.2 degrees of the depot.
  return {
    lat: DEPOT.lat + ((hash % 400) - 200) / 1000,
    lng: DEPOT.lng + ((Math.floor(hash / 400) % 400) - 200) / 1000,
  };
}

/** Normalises whatever the caller sent into a list of {label, lat, lng}. */
function readStops(body) {
  if (Array.isArray(body.stops) && body.stops.length > 0) {
    return body.stops.map((stop, index) => {
      const label = stop.label || stop.address || `Stop ${index + 1}`;
      const hasCoordinates = typeof stop.lat === 'number' && typeof stop.lng === 'number';
      const point = hasCoordinates ? { lat: stop.lat, lng: stop.lng } : coordinatesForAddress(label);

      return { label, lat: point.lat, lng: point.lng };
    });
  }

  if (body.deliveryAddress) {
    const point = coordinatesForAddress(body.deliveryAddress);
    return [{ label: body.deliveryAddress, lat: point.lat, lng: point.lng }];
  }

  return [];
}

/**
 * Orders the stops nearest-first and works out arrival times.
 *
 * Returns the sequenced stops plus the totals for the whole trip.
 */
function optimize(stops) {
  const remaining = [...stops];
  const sequenced = [];

  let current = DEPOT;
  let totalDistanceKm = 0;
  let elapsedMinutes = 0;

  while (remaining.length > 0) {
    let nearestIndex = 0;
    let nearestDistance = distanceKm(current, remaining[0]);

    for (let i = 1; i < remaining.length; i += 1) {
      const candidateDistance = distanceKm(current, remaining[i]);
      if (candidateDistance < nearestDistance) {
        nearestIndex = i;
        nearestDistance = candidateDistance;
      }
    }

    const [nearest] = remaining.splice(nearestIndex, 1);

    totalDistanceKm += nearestDistance;
    elapsedMinutes += (nearestDistance / AVERAGE_SPEED_KMH) * 60 + MINUTES_PER_DROP_OFF;

    sequenced.push({
      sequence: sequenced.length + 1,
      label: nearest.label,
      lat: Number(nearest.lat.toFixed(6)),
      lng: Number(nearest.lng.toFixed(6)),
      distanceFromPreviousKm: Number(nearestDistance.toFixed(2)),
      etaMinutes: Math.round(elapsedMinutes),
    });

    current = nearest;
  }

  return {
    stops: sequenced,
    totalDistanceKm: Number(totalDistanceKm.toFixed(2)),
    estimatedDurationMinutes: Math.round(elapsedMinutes),
  };
}

module.exports = { DEPOT, optimize, readStops };
