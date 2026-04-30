import { useEffect, useMemo, useState } from 'react';
import { CircleMarker, MapContainer, Popup, TileLayer, Tooltip, useMap } from 'react-leaflet';
import { LatLngBounds } from 'leaflet';
import 'leaflet/dist/leaflet.css';

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

function markerRadius(visits, maxVisits) {
  if (!maxVisits) {
    return 10;
  }
  const normalized = visits / maxVisits;
  return Math.max(9, Math.min(24, 8 + normalized * 16));
}

function markerColor(visits, maxVisits) {
  if (!maxVisits) {
    return '#c98345';
  }
  const normalized = visits / maxVisits;
  return normalized > 0.66 ? '#9f5d20' : normalized > 0.33 ? '#c98345' : '#e4ae68';
}

function pointKey(point) {
  return `${point.locationName}-${point.latitude}-${point.longitude}`;
}

function FitMapToPoints({ points }) {
  const map = useMap();

  useEffect(() => {
    if (!points.length) {
      return;
    }

    const focusPoints = points.slice(0, Math.min(points.length, 12));

    if (focusPoints.length === 1) {
      map.setView([focusPoints[0].latitude, focusPoints[0].longitude], 12, { animate: false });
      return;
    }

    const bounds = new LatLngBounds(focusPoints.map((point) => [point.latitude, point.longitude]));
    map.fitBounds(bounds, {
      animate: false,
      padding: [36, 36],
      maxZoom: 12
    });
  }, [map, points]);

  useEffect(() => {
    const timer = window.setTimeout(() => map.invalidateSize(), 150);
    return () => window.clearTimeout(timer);
  }, [map, points]);

  return null;
}

function FocusMapOnPoint({ point }) {
  const map = useMap();

  useEffect(() => {
    if (!point) {
      return;
    }
    map.setView([point.latitude, point.longitude], 13, { animate: false });
  }, [map, point]);

  return null;
}

function buildGoogleMapsUrl(point) {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${point.latitude},${point.longitude}`)}`;
}

export default function SiteVisitMapSection({ points }) {
  const safePoints = useMemo(
    () => (points || []).filter((point) => point.latitude != null && point.longitude != null),
    [points]
  );
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

  if (!safePoints.length) {
    return (
      <div className="site-map-empty">
        <strong>No mapped visits yet</strong>
        <span>Locations will start showing here as browsers share approximate coordinates.</span>
      </div>
    );
  }

  return (
    <div className="site-map-layout">
      <div className="site-visit-map-panel">
        <MapContainer
          center={[safePoints[0].latitude, safePoints[0].longitude]}
          zoom={5}
          scrollWheelZoom={false}
          className="site-visit-map"
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <FitMapToPoints points={safePoints} />
          {safePoints.map((point) => {
            const color = markerColor(point.visits, maxVisits);
            return (
              <CircleMarker
                key={pointKey(point)}
                center={[point.latitude, point.longitude]}
                radius={markerRadius(point.visits, maxVisits)}
                pathOptions={{
                  color,
                  fillColor: color,
                  fillOpacity: 0.55,
                  weight: 2
                }}
                eventHandlers={{
                  click: () => setSelectedPointKey(pointKey(point))
                }}
              >
                <Tooltip direction="top" offset={[0, -8]}>{point.locationName}</Tooltip>
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
        <div className="site-map-legend">
          <span>{numberFormat(totalMappedVisits)} mapped visits</span>
          <span>{numberFormat(safePoints.length)} hotspots</span>
          <span>More visits = larger, darker marker</span>
        </div>
      </div>

      <div className="site-map-sidebar">
        {selectedPoint ? (
          <div className="site-map-focus-card">
            <div className="site-map-focus-head">
              <h3>Most visited area</h3>
              <span>{numberFormat(selectedPoint.visits)} visits</span>
            </div>
            <MapContainer
              center={[selectedPoint.latitude, selectedPoint.longitude]}
              zoom={13}
              scrollWheelZoom={false}
              className="site-visit-map site-visit-map-focus"
            >
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              />
              <FocusMapOnPoint point={selectedPoint} />
              <CircleMarker
                center={[selectedPoint.latitude, selectedPoint.longitude]}
                radius={16}
                pathOptions={{
                  color: markerColor(selectedPoint.visits, maxVisits),
                  fillColor: markerColor(selectedPoint.visits, maxVisits),
                  fillOpacity: 0.55,
                  weight: 2
                }}
              >
                <Popup>
                  <div className="site-map-popup">
                    <strong>{selectedPoint.locationName}</strong>
                    <span>{numberFormat(selectedPoint.visits)} visits</span>
                  </div>
                </Popup>
              </CircleMarker>
            </MapContainer>
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
                href={buildGoogleMapsUrl(selectedPoint)}
                target="_blank"
                rel="noreferrer"
              >
                Open in Google Maps
              </a>
            </div>
          </div>
        ) : null}
        <h3>Top hotspots</h3>
        <div className="site-map-hotspot-list">
          {safePoints.slice(0, 8).map((point, index) => (
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
                {point.postalCode ? `PIN ${point.postalCode} · ` : ''}{formatVisitTimestamp(point.latestVisitAt)}
              </div>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
