import { useEffect, useMemo, useState } from 'react';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { CircleMarker, MapContainer, Popup, TileLayer, useMap } from 'react-leaflet';

const MAHARASHTRA_BOUNDS = {
  south: 15.55,
  north: 22.15,
  west: 72.55,
  east: 80.95
};

const LOCAL_CLUSTER_RADIUS_KM = 30;
const AREA_MATCH_RADIUS_KM = 45;

function numberFormat(value) {
  return new Intl.NumberFormat('en-IN').format(Number(value || 0));
}

function formatVisitTimestamp(value) {
  if (!value) {
    return '-';
  }
  return `${new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
    timeZone: 'Asia/Kolkata'
  }).format(new Date(value))} IST`;
}

function formatSourceTypeLabel(value) {
  switch (value) {
    case 'CAMPAIGN':
      return 'Campaign';
    case 'SEARCH':
      return 'Search';
    case 'SOCIAL':
      return 'Social';
    case 'REFERRAL':
      return 'Referral';
    case 'DIRECT':
    default:
      return 'Direct';
  }
}

function pointKey(point) {
  return `${point.locationName}-${point.latitude}-${point.longitude}`;
}

function markerSize(visits, maxVisits) {
  if (!maxVisits) {
    return 14;
  }
  const normalized = Number(visits || 0) / maxVisits;
  return Math.round(Math.max(12, Math.min(28, 12 + normalized * 16)));
}

function markerColor(visits, maxVisits) {
  if (!maxVisits) {
    return '#c98345';
  }
  const normalized = Number(visits || 0) / maxVisits;
  if (normalized > 0.66) {
    return '#9f5d20';
  }
  if (normalized > 0.33) {
    return '#c98345';
  }
  return '#e4ae68';
}

function degreesToRadians(value) {
  return (value * Math.PI) / 180;
}

function distanceKm(left, right) {
  const earthRadiusKm = 6371;
  const dLat = degreesToRadians(right.latitude - left.latitude);
  const dLng = degreesToRadians(right.longitude - left.longitude);
  const lat1 = degreesToRadians(left.latitude);
  const lat2 = degreesToRadians(right.latitude);

  const haversine = Math.sin(dLat / 2) ** 2
    + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;

  return 2 * earthRadiusKm * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
}

function comparableLabel(value) {
  return String(value || '')
    .toLowerCase()
    .replace(/[^a-z\u0900-\u097f0-9]+/g, ' ')
    .trim();
}

function extractAreaLabel(point) {
  const exactParts = String(point.exactLocationName || '')
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean);
  if (exactParts.length >= 3) {
    return exactParts[1];
  }
  if (exactParts.length >= 2) {
    return exactParts[0];
  }

  const locationParts = String(point.locationName || '')
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean);
  return locationParts[0] || point.locationName || 'Maharashtra';
}

function buildOpenStreetMapUrl(point) {
  return `https://www.openstreetmap.org/?mlat=${encodeURIComponent(point.latitude)}&mlon=${encodeURIComponent(point.longitude)}#map=13/${encodeURIComponent(point.latitude)}/${encodeURIComponent(point.longitude)}`;
}

function isWithinMaharashtraBounds(latitude, longitude) {
  return latitude >= MAHARASHTRA_BOUNDS.south
    && latitude <= MAHARASHTRA_BOUNDS.north
    && longitude >= MAHARASHTRA_BOUNDS.west
    && longitude <= MAHARASHTRA_BOUNDS.east;
}

function isMaharashtraPoint(point) {
  const regionText = `${point.region || ''} ${point.locationName || ''} ${point.exactLocationName || ''}`.toLowerCase();
  if (regionText.includes('maharashtra')) {
    return true;
  }
  if (point.latitude == null || point.longitude == null) {
    return false;
  }
  return isWithinMaharashtraBounds(point.latitude, point.longitude);
}

function focusCluster(points, selectedPoint) {
  if (!selectedPoint) {
    return points;
  }

  const nearbyPoints = points.filter((point) => distanceKm(selectedPoint, point) <= LOCAL_CLUSTER_RADIUS_KM);
  if (nearbyPoints.length > 1) {
    return nearbyPoints;
  }

  const selectedArea = comparableLabel(extractAreaLabel(selectedPoint));
  const sameAreaPoints = points.filter((point) => {
    const pointArea = comparableLabel(extractAreaLabel(point));
    return pointArea === selectedArea && distanceKm(selectedPoint, point) <= AREA_MATCH_RADIUS_KM;
  });
  if (sameAreaPoints.length > 1) {
    return sameAreaPoints;
  }

  return nearbyPoints.length ? nearbyPoints : [selectedPoint];
}

function MapViewportController({ points, selectedPoint, focusPoints }) {
  const map = useMap();

  useEffect(() => {
    if (!points.length) {
      return;
    }

    if (selectedPoint) {
      if (focusPoints.length > 1) {
        const bounds = L.latLngBounds(focusPoints.map((point) => [point.latitude, point.longitude]));
        map.fitBounds(bounds, { padding: [34, 34], maxZoom: 12 });
      } else {
        map.setView([selectedPoint.latitude, selectedPoint.longitude], Math.max(map.getZoom() || 0, 12), {
          animate: true
        });
      }
      return;
    }

    if (points.length === 1) {
      map.setView([points[0].latitude, points[0].longitude], 11, { animate: true });
      return;
    }

    const bounds = L.latLngBounds(points.map((point) => [point.latitude, point.longitude]));
    map.fitBounds(bounds, { padding: [32, 32] });
  }, [map, points, selectedPoint, focusPoints]);

  return null;
}

export default function SiteVisitMapSection({ points }) {
  const safePoints = useMemo(() => {
    const filtered = (points || [])
      .filter((point) => point.latitude != null && point.longitude != null)
      .filter(isMaharashtraPoint);
    return [...filtered].sort((left, right) => Number(right.visits || 0) - Number(left.visits || 0));
  }, [points]);

  const maxVisits = useMemo(
    () => safePoints.reduce((max, point) => Math.max(max, Number(point.visits || 0)), 0),
    [safePoints]
  );
  const totalMappedVisits = useMemo(
    () => safePoints.reduce((total, point) => total + Number(point.visits || 0), 0),
    [safePoints]
  );

  const [selectedPointKey, setSelectedPointKey] = useState('');

  useEffect(() => {
    if (!safePoints.length) {
      setSelectedPointKey('');
      return;
    }
    setSelectedPointKey((current) => {
      if (current && safePoints.some((point) => pointKey(point) === current)) {
        return current;
      }
      return pointKey(safePoints[0]);
    });
  }, [safePoints]);

  const selectedPoint = useMemo(
    () => safePoints.find((point) => pointKey(point) === selectedPointKey) || safePoints[0] || null,
    [safePoints, selectedPointKey]
  );
  const selectedClusterPoints = useMemo(
    () => focusCluster(safePoints, selectedPoint),
    [safePoints, selectedPoint]
  );
  const selectedClusterKeys = useMemo(
    () => new Set(selectedClusterPoints.map((point) => pointKey(point))),
    [selectedClusterPoints]
  );
  const selectedAreaLabel = selectedPoint ? extractAreaLabel(selectedPoint) : 'Maharashtra';
  const selectedClusterVisits = useMemo(
    () => selectedClusterPoints.reduce((total, point) => total + Number(point.visits || 0), 0),
    [selectedClusterPoints]
  );

  const centerPoint = selectedPoint || safePoints[0] || null;

  if (!safePoints.length) {
    return (
      <div className="site-map-empty">
        <strong>No Maharashtra visit locations yet</strong>
        <span>Only Maharashtra storefront visit locations are shown in this map view.</span>
      </div>
    );
  }

  return (
    <div className="site-map-layout">
      <div className="site-visit-map-panel">
        <div className="site-visit-map-shell">
          <MapContainer
            center={[centerPoint.latitude, centerPoint.longitude]}
            zoom={7}
            scrollWheelZoom={false}
            className="site-visit-map"
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <MapViewportController points={safePoints} selectedPoint={selectedPoint} focusPoints={selectedClusterPoints} />
            {safePoints.map((point) => {
              const key = pointKey(point);
              const isSelected = selectedPoint && key === pointKey(selectedPoint);
              const isInSelectedCluster = selectedClusterKeys.has(key);
              return (
                <CircleMarker
                  key={key}
                  center={[point.latitude, point.longitude]}
                  radius={markerSize(point.visits, maxVisits)}
                  pathOptions={{
                    color: isSelected ? '#0f4f4b' : isInSelectedCluster ? '#7d4e1c' : '#fff7ec',
                    weight: isSelected ? 5 : isInSelectedCluster ? 4 : 2,
                    fillColor: markerColor(point.visits, maxVisits),
                    fillOpacity: isSelected ? 0.96 : isInSelectedCluster ? 0.86 : 0.38
                  }}
                  eventHandlers={{
                    click: () => setSelectedPointKey(key)
                  }}
                >
                  <Popup>
                    <div className="site-map-popup">
                      <strong>{point.locationName}</strong>
                      <span>{numberFormat(point.visits)} visits</span>
                      <span>{formatSourceTypeLabel(point.sourceType)} · {point.sourceLabel || 'Direct'}</span>
                      <span>{formatVisitTimestamp(point.latestVisitAt)}</span>
                    </div>
                  </Popup>
                </CircleMarker>
              );
            })}
          </MapContainer>
        </div>
        <div className="site-map-legend">
          <span>{numberFormat(totalMappedVisits)} Maharashtra visits</span>
          <span>{numberFormat(safePoints.length)} hotspots</span>
          <span>{selectedAreaLabel} cluster · {numberFormat(selectedClusterVisits)} visits</span>
          <span>OpenStreetMap view · more visits = larger, darker marker</span>
        </div>
      </div>

      <div className="site-map-sidebar">
        {selectedPoint ? (
          <div className="site-map-focus-card">
            <div className="site-map-focus-head">
              <h3>{selectedPointKey === pointKey(safePoints[0]) ? 'Busiest Maharashtra location' : 'Focused Maharashtra hotspot'}</h3>
              <span>{numberFormat(selectedPoint.visits)} visits</span>
            </div>
            <div className="site-map-focus-meta">
              <strong>{selectedPoint.locationName}</strong>
              <span>{formatSourceTypeLabel(selectedPoint.sourceType)} · {selectedPoint.sourceLabel || 'Direct'}</span>
              <span>
                {selectedPoint.postalCode ? `PIN ${selectedPoint.postalCode} · ` : ''}
                {selectedPoint.latitude.toFixed(5)}, {selectedPoint.longitude.toFixed(5)}
              </span>
              <span>{formatVisitTimestamp(selectedPoint.latestVisitAt)}</span>
              <a
                className="ghost-btn compact-btn site-map-open-link"
                href={buildOpenStreetMapUrl(selectedPoint)}
                target="_blank"
                rel="noreferrer"
              >
                Open in OpenStreetMap
              </a>
            </div>
          </div>
        ) : null}

        <h3>Top Maharashtra hotspots</h3>
        <div className="site-map-hotspot-list">
          {safePoints.slice(0, 8).map((point) => (
            <button
              key={pointKey(point)}
              type="button"
              className={`site-map-hotspot-card site-map-hotspot-button ${selectedPoint && pointKey(point) === pointKey(selectedPoint) ? 'active' : ''}`}
              onClick={() => setSelectedPointKey(pointKey(point))}
            >
              <div className="site-map-hotspot-head">
                <strong>{point.locationName}</strong>
                <span>{numberFormat(point.visits)} visits</span>
              </div>
              <div className="table-subcopy">
                {formatSourceTypeLabel(point.sourceType)} · {point.sourceLabel || 'Direct'}
              </div>
              <div className="table-subcopy">
                {point.postalCode ? `PIN ${point.postalCode} · ` : ''}
                {formatVisitTimestamp(point.latestVisitAt)}
              </div>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
