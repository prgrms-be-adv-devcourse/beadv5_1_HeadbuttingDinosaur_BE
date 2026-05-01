import { Link } from "react-router-dom";
import type { EventItem } from "../api/types";
import { EVENT_STATUS } from "../constants";
import { formatDateTime, formatPrice } from "../utils";

const ACCENT_COLORS = [
  "#4F46E5",
  "#0EA5E9",
  "#10B981",
  "#F59E0B",
  "#8B5CF6",
  "#EC4899",
  "#EF4444",
];

function accentColor(seed: string) {
  return ACCENT_COLORS[seed.charCodeAt(0) % ACCENT_COLORS.length];
}

interface Props {
  event: EventItem;
}

export default function EventCard({ event }: Props) {
  const status = EVENT_STATUS[event.status as keyof typeof EVENT_STATUS] ?? {
    label: event.status,
    badge: "badge-gray",
    color: "var(--text-3)",
  };

  const color = accentColor(event.eventId);
  const isFree = event.price === 0;
  const isLowStock = event.status === "ON_SALE" && event.remainingQuantity < 10;

  return (
    <Link to={`/events/${event.eventId}`} style={{ display: "block" }}>
      <article
        style={{
          background: "var(--surface)",
          border: "1px solid var(--border)",
          borderRadius: "var(--r-lg)",
          overflow: "hidden",
          transition: "transform 0.18s ease, box-shadow 0.18s ease",
          cursor: "pointer",
        }}
        onMouseEnter={(e) => {
          (e.currentTarget as HTMLElement).style.transform = "translateY(-4px)";
          (e.currentTarget as HTMLElement).style.boxShadow =
            "0 12px 32px rgba(0,0,0,0.12)";
        }}
        onMouseLeave={(e) => {
          (e.currentTarget as HTMLElement).style.transform = "translateY(0)";
          (e.currentTarget as HTMLElement).style.boxShadow = "";
        }}
      >
        {/* Thumbnail */}
        <div
          style={{
            height: 148,
            position: "relative",
            overflow: "hidden",
            background: event.thumbnailUrl
              ? `url(${event.thumbnailUrl}) center/cover`
              : `linear-gradient(135deg, ${color}18 0%, ${color}38 100%)`,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          {!event.thumbnailUrl && (
            <span
              style={{
                fontFamily: "var(--font-mono)",
                fontSize: 40,
                color,
                opacity: 0.35,
                userSelect: "none",
              }}
            >
              {"</>"}
            </span>
          )}

          {/* Status badge */}
          <div style={{ position: "absolute", top: 10, left: 10 }}>
            <span className={`badge ${status.badge}`}>{status.label}</span>
          </div>

          {/* Free badge */}
          {isFree && (
            <div style={{ position: "absolute", top: 10, right: 10 }}>
              <span className="badge badge-brand">무료</span>
            </div>
          )}

          {/* Low stock warning */}
          {isLowStock && (
            <div
              style={{
                position: "absolute",
                bottom: 8,
                left: 10,
                display: "flex",
                alignItems: "center",
                gap: 4,
                padding: "2px 8px",
                borderRadius: "var(--r-full)",
                background: "rgba(239,68,68,0.9)",
                color: "#fff",
                fontSize: 11,
                fontWeight: 600,
              }}
            >
              ⚡ 잔여 {event.remainingQuantity}석
            </div>
          )}
        </div>

        {/* Body */}
        <div style={{ padding: "14px 16px 16px" }}>
          {/* Category */}
          <div
            style={{
              fontSize: 11,
              fontWeight: 600,
              color,
              fontFamily: "var(--font-mono)",
              marginBottom: 5,
              opacity: 0.85,
            }}
          >
            {event.category}
          </div>

          {/* Title */}
          <h3
            style={{
              fontSize: 15,
              fontWeight: 600,
              color: "var(--text)",
              lineHeight: 1.4,
              marginBottom: 10,
              overflow: "hidden",
              display: "-webkit-box",
              WebkitLineClamp: 2,
              WebkitBoxOrient: "vertical",
              minHeight: "2.8em",
            }}
          >
            {event.title}
          </h3>

          {/* Tech stacks */}
          {event.techStacks?.length > 0 && (
            <div
              style={{
                display: "flex",
                flexWrap: "wrap",
                gap: 4,
                marginBottom: 12,
              }}
            >
              {event.techStacks.slice(0, 3).map((t, i) => (
                <span
                  key={i}
                  className="tag"
                  style={{ fontSize: 11, padding: "2px 8px" }}
                >
                  {t}
                </span>
              ))}
              {event.techStacks.length > 3 && (
                <span
                  className="tag"
                  style={{ fontSize: 11, padding: "2px 8px" }}
                >
                  +{event.techStacks.length - 3}
                </span>
              )}
            </div>
          )}

          {/* Meta footer */}
          <div
            style={{
              borderTop: "1px solid var(--border)",
              paddingTop: 10,
              marginTop: "auto",
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
            }}
          >
            <div style={{ fontSize: 12, color: "var(--text-3)" }}>
              📅 {formatDateTime(event.eventDateTime)}
            </div>
            <div
              style={{
                fontSize: 15,
                fontWeight: 700,
                color: isFree ? "var(--success)" : "var(--text)",
              }}
            >
              {formatPrice(event.price)}
            </div>
          </div>
        </div>
      </article>
    </Link>
  );
}
