import { useEffect } from 'react'
import { CircleMarker, MapContainer, Polyline, TileLayer, Tooltip, useMap } from 'react-leaflet'
import type { LatLngBoundsExpression, LatLngTuple } from 'leaflet'
import type { RouteDto } from '@/lib/types'

function Fit({ bounds }: { bounds: LatLngBoundsExpression }) {
  const map = useMap()
  useEffect(() => {
    map.fitBounds(bounds, { padding: [28, 28] })
  }, [map, bounds])
  return null
}

/** Real road geometry from the routing engine, on OpenStreetMap tiles (no API key needed). */
export function RouteMap({ route, className }: { route: RouteDto; className?: string }) {
  const line: LatLngTuple[] = route.geometry.length > 1 ? route.geometry.map(([a, b]) => [a, b] as LatLngTuple) : [[route.from.lat, route.from.lon], [route.to.lat, route.to.lon]]
  const dashed = !route.measured || route.geometry.length < 2
  return (
    <div className={className} role="img" aria-label={`Map of the route from ${route.from.name} to ${route.to.name}`}>
      <MapContainer center={[route.from.lat, route.from.lon]} zoom={6} scrollWheelZoom={false} className="size-full rounded-2xl" attributionControl>
        <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
        <Polyline positions={line} pathOptions={{ color: '#f97316', weight: 4, opacity: 0.9, dashArray: dashed ? '8 8' : undefined }} />
        <CircleMarker center={[route.from.lat, route.from.lon]} radius={8} pathOptions={{ color: '#fff', weight: 3, fillColor: '#0b1f3a', fillOpacity: 1 }}>
          <Tooltip permanent direction="top" offset={[0, -8]}>
            {route.from.name}
          </Tooltip>
        </CircleMarker>
        <CircleMarker center={[route.to.lat, route.to.lon]} radius={8} pathOptions={{ color: '#fff', weight: 3, fillColor: '#f97316', fillOpacity: 1 }}>
          <Tooltip permanent direction="top" offset={[0, -8]}>
            {route.to.name}
          </Tooltip>
        </CircleMarker>
        <Fit bounds={line} />
      </MapContainer>
    </div>
  )
}
