import { useCallback, useState, useEffect } from "react";
import {
  getEvents,
  searchEvents,
  filterEvents,
} from "../api/events.api";
import { getTechStacks } from "../api/auth.api";
import type { EventItem } from "../api/types";
import EventCard from "../components/EventCard";
import Pagination from "../components/Pagination";
import { usePagedApi } from "../hooks/useApi";
import { useDebounce } from "../hooks/useDebounce";

const CATEGORIES = ["전체", "컨퍼런스", "밋업", "해커톤", "스터디", "프로젝트"];

const CATEGORY_MAP: Record<string, string> = {
  컨퍼런스: "CONFERENCE",
  밋업: "MEETUP",
  해커톤: "HACKATHON",
  스터디: "STUDY",
  프로젝트: "PROJECT",
};

export default function EventList() {
  const [keyword, setKeyword] = useState("");
  const [category, setCategory] = useState("전체");
  const [selectedStackId, setSelectedStackId] = useState<number | null>(null);
  const [techStacks, setTechStacks] = useState<
    { techStackId: number; name: string }[]
  >([]);

  useEffect(() => {
    getTechStacks().then((res) => setTechStacks(res.data.techStacks));
  }, []);

  const debouncedKeyword = useDebounce(keyword, 350);

  const apiFn = useCallback(
    (page: number) => {
      if (debouncedKeyword) {
        return searchEvents({ keyword: debouncedKeyword, page, size: 12 });
      }
      if (category !== "전체" || selectedStackId) {
        return filterEvents({
          category: category !== "전체" ? CATEGORY_MAP[category] : undefined,
          techStacks: selectedStackId ? [selectedStackId] : undefined,
          page,
          size: 12,
        });
      }
      return getEvents({ page, size: 12 });
    },
    [debouncedKeyword, category, selectedStackId],
  );

  const { items, page, totalPages, totalElements, loading, changePage } =
    usePagedApi<EventItem>(apiFn);

  const reset = () => {
    setKeyword("");
    setCategory("전체");
    setSelectedStackId(null);
  };

  const hasFilter = category !== "전체" || selectedStackId || keyword;

  return (
    <div className="container" style={{ paddingTop: 44, paddingBottom: 72 }}>
      {/* Hero */}
      <div style={{ textAlign: "center", marginBottom: 44 }}>
        <div
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 6,
            padding: "4px 14px",
            borderRadius: "var(--r-full)",
            background: "var(--brand-light)",
            color: "var(--brand)",
            fontSize: 12,
            fontWeight: 600,
            marginBottom: 16,
            fontFamily: "var(--font-mono)",
            letterSpacing: "0.02em",
          }}
        >
          ✦ 개발자를 위한 이벤트 플랫폼
        </div>
        <h1
          style={{
            fontSize: 34,
            fontWeight: 800,
            color: "var(--text)",
            marginBottom: 10,
            letterSpacing: "-0.02em",
          }}
        >
          다음 컨퍼런스를 찾아보세요
        </h1>
        <p style={{ fontSize: 16, color: "var(--text-3)" }}>
          {!loading && totalElements > 0
            ? `${totalElements.toLocaleString()}개의 이벤트가 기다리고 있습니다`
            : "관심 기술 스택의 밋업 · 컨퍼런스를 찾아보세요"}
        </p>
      </div>

      {/* Search */}
      <div
        style={{
          display: "flex",
          gap: 8,
          maxWidth: 560,
          margin: "0 auto 32px",
        }}
      >
        <div className="search-bar" style={{ flex: 1, position: "relative" }}>
          <svg
            className="search-icon"
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
          >
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.35-4.35" />
          </svg>
          <input
            className="search-input"
            placeholder="이벤트명, 기술 스택 검색..."
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value);
              changePage(0);
            }}
          />
          {keyword && (
            <button
              onClick={() => {
                setKeyword("");
                changePage(0);
              }}
              style={{
                position: "absolute",
                right: 10,
                top: "50%",
                transform: "translateY(-50%)",
                background: "none",
                border: "none",
                cursor: "pointer",
                color: "var(--text-4)",
                fontSize: 16,
                padding: 4,
                lineHeight: 1,
              }}
            >
              ✕
            </button>
          )}
        </div>
        {hasFilter && (
          <button className="btn btn-secondary" onClick={reset}>
            초기화
          </button>
        )}
      </div>

      {/* Category chips */}
      <div
        style={{
          display: "flex",
          gap: 6,
          flexWrap: "wrap",
          marginBottom: 10,
          justifyContent: "center",
        }}
      >
        {CATEGORIES.map((cat) => (
          <button
            key={cat}
            onClick={() => {
              setCategory(cat);
              changePage(0);
            }}
            style={{
              padding: "6px 16px",
              borderRadius: "var(--r-full)",
              fontSize: 13,
              fontWeight: category === cat ? 600 : 400,
              border: `1.5px solid ${category === cat ? "var(--brand)" : "var(--border)"}`,
              background:
                category === cat ? "var(--brand-light)" : "var(--surface)",
              color: category === cat ? "var(--brand)" : "var(--text-2)",
              cursor: "pointer",
              transition: "all 0.12s",
            }}
          >
            {cat}
          </button>
        ))}
      </div>

      {/* Tech stack chips */}
      <div
        style={{
          display: "flex",
          gap: 6,
          flexWrap: "wrap",
          justifyContent: "center",
          marginBottom: 36,
        }}
      >
        {techStacks.map((stack) => (
          <button
            key={stack.techStackId}
            onClick={() => {
              setSelectedStackId((id) => (id === stack.techStackId ? null : stack.techStackId));
              changePage(0);
            }}
            className="tag"
            style={{
              cursor: "pointer",
              background:
                selectedStackId === stack.techStackId
                  ? "var(--brand-light)"
                  : "var(--surface-2)",
              color:
                selectedStackId === stack.techStackId ? "var(--brand)" : "var(--text-2)",
              border: `1px solid ${selectedStackId === stack.techStackId ? "var(--brand-muted)" : "var(--border)"}`,
              transition: "all 0.12s",
            }}
          >
            {stack.name}
          </button>
        ))}
      </div>

      {/* Active filter chips */}
      {(keyword || selectedStackId) && (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 8,
            marginBottom: 20,
            flexWrap: "wrap",
          }}
        >
          <span style={{ fontSize: 13, color: "var(--text-3)" }}>필터:</span>
          {keyword && (
            <ActiveChip
              label={`"${keyword}"`}
              onRemove={() => setKeyword("")}
            />
          )}
          {selectedStackId && (
            <ActiveChip
              label={techStacks.find((s) => s.techStackId === selectedStackId)?.name ?? ""}
              onRemove={() => setSelectedStackId(null)}
            />
          )}
        </div>
      )}

      {/* Grid */}
      {loading ? (
        <SkeletonGrid />
      ) : items.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">🔍</div>
          <div className="empty-title">이벤트가 없습니다</div>
          <div className="empty-desc">다른 조건으로 검색해보세요</div>
          <button
            className="btn btn-secondary"
            style={{ marginTop: 8 }}
            onClick={reset}
          >
            전체 보기
          </button>
        </div>
      ) : (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
            gap: 20,
          }}
        >
          {items.map((event) => (
            <EventCard key={event.eventId} event={event} />
          ))}
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} onChange={changePage} />
    </div>
  );
}

function ActiveChip({
  label,
  onRemove,
}: {
  label: string;
  onRemove: () => void;
}) {
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
        padding: "3px 10px",
        background: "var(--brand-light)",
        color: "var(--brand)",
        borderRadius: "var(--r-full)",
        fontSize: 13,
        fontWeight: 500,
      }}
    >
      {label}
      <button
        onClick={onRemove}
        style={{
          background: "none",
          border: "none",
          cursor: "pointer",
          color: "var(--brand)",
          lineHeight: 1,
          padding: 0,
          fontSize: 14,
        }}
      >
        ✕
      </button>
    </span>
  );
}

function SkeletonGrid() {
  return (
    <>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
          gap: 20,
        }}
      >
        {Array.from({ length: 8 }).map((_, i) => (
          <div
            key={i}
            style={{
              height: 280,
              borderRadius: "var(--r-lg)",
              background: "var(--surface)",
              border: "1px solid var(--border)",
              overflow: "hidden",
              position: "relative",
            }}
          >
            <div style={{ height: 140, background: "var(--surface-2)" }} />
            <div
              style={{
                padding: "14px 16px",
                display: "flex",
                flexDirection: "column",
                gap: 10,
              }}
            >
              <div
                style={{
                  height: 10,
                  width: "40%",
                  background: "var(--surface-3)",
                  borderRadius: 4,
                }}
              />
              <div
                style={{
                  height: 14,
                  width: "85%",
                  background: "var(--surface-3)",
                  borderRadius: 4,
                }}
              />
              <div
                style={{
                  height: 14,
                  width: "65%",
                  background: "var(--surface-3)",
                  borderRadius: 4,
                }}
              />
            </div>
            <div
              style={{
                position: "absolute",
                inset: 0,
                background:
                  "linear-gradient(90deg, transparent 0%, rgba(255,255,255,0.5) 50%, transparent 100%)",
                backgroundSize: "200% 100%",
                animation: "shimmer 1.5s infinite",
              }}
            />
          </div>
        ))}
      </div>
      <style>{`@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }`}</style>
    </>
  );
}
