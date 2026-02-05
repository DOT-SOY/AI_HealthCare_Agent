import { useState, useEffect } from "react";
import { useLocation } from "react-router-dom";
import { Crown, Trophy, Medal } from "lucide-react";
import { getRanking } from "../../services/rankingApi";

const PURPOSE_LABEL = {
  DIET: "다이어트",
  MAINTAIN: "유지",
  BULK_UP: "벌크업",
};

const RANK_ICONS = { 1: Crown, 2: Trophy, 3: Medal };
const RANK_BADGE = {
  1: "오늘의 챔피언",
  2: "준우승",
  3: "3위 도전자",
};
const RANK_SUB = {
  1: "루틴·식단 수행률 1위 🔥",
  2: "바짝 따라오는 중 🥈",
  3: "다음 주 2등? 🥉",
};

function RankingList() {
  const location = useLocation();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [period] = useState(30);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    getRanking(period)
      .then((res) => {
        if (!cancelled && res?.data) setData(res.data);
      })
      .catch((err) => {
        if (!cancelled) setError(err?.message || "랭킹을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [period, location.pathname]);

  if (loading) {
    return (
      <div className="w-full dashboard-container mt-6">
        <header className="section-header-token">
          <h1 className="section-title">
            <span className="text-text-main">Today's </span>
            <span className="text-primary-500">Rank</span>
          </h1>
        </header>
        <div className="flex justify-center items-center py-16 text-text-sub">로딩 중...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="w-full dashboard-container mt-6">
        <header className="section-header-token">
          <h1 className="section-title">
            <span className="text-text-main">Today's </span>
            <span className="text-primary-500">Rank</span>
          </h1>
        </header>
        <div className="text-accent-secondary py-4">{error}</div>
      </div>
    );
  }

  const myPurpose = data?.myPurpose ?? null;
  const myGroupSize = data?.myGroupSize ?? 0;
  const myRankInGroup = data?.myRankInGroup ?? null;
  const myRoutineRate = data?.myRoutineRate ?? null;
  const myMealRate = data?.myMealRate ?? null;
  const myCombinedRate = data?.myCombinedRate ?? null;
  const groups = data?.groups ?? {};
  const myGroup = myPurpose ? groups[myPurpose] : null;
  const top3 = myGroup?.top3 ?? [];
  const fullList = myGroup?.fullList ?? [];

  return (
    <div className="w-full dashboard-container mt-6">
      <header className="section-header-token">
        <h1 className="section-title">
          <span className="text-text-main">Today's </span>
          <span className="text-primary-500">Rank</span>
        </h1>
        {period > 0 && (
          <p className="section-desc text-text-sub">최근 {period}일 기준 (루틴·식단 수행률)</p>
        )}
      </header>

      {/* 내 그룹 + 순위 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
        <div className="info-card">
          <div className="flex items-center gap-2 mb-3">
            <span className="text-text-main font-display font-bold">내 그룹</span>
          </div>
          <div className="flex flex-wrap gap-2 mb-3">
            <span className="px-3 py-1 rounded-full text-sm font-medium border border-primary-500 text-primary-500 bg-primary-500/10">
              {myPurpose ? PURPOSE_LABEL[myPurpose] ?? myPurpose : "미설정"}
            </span>
          </div>
          <p className="text-text-sub text-sm">
            {myGroupSize > 0
              ? `같은 운동 목적을 가진 ${myGroupSize}명과 경쟁하고 있습니다`
              : "운동 목적을 설정하면 그룹 랭킹에 참여할 수 있습니다."}
          </p>
        </div>

        <div className="info-card flex flex-col justify-center items-center text-center">
          <span className="text-text-sub text-sm font-medium mb-1">순위</span>
          <p className="text-text-main text-2xl font-bold">
            {myGroupSize > 0 && myRankInGroup != null ? (
              <>{myGroupSize}명 중 <span className="text-primary-500">{myRankInGroup}위</span></>
            ) : (
              <span className="text-text-sub">-</span>
            )}
          </p>
          {myCombinedRate != null && (
            <p className="text-text-sub text-sm mt-1">합한 수행률 {myCombinedRate.toFixed(1)}%</p>
          )}
          {(myRoutineRate != null || myMealRate != null) && (
            <p className="text-text-sub text-xs mt-0.5">
              루틴 {myRoutineRate?.toFixed(0) ?? "-"}% / 식단 {myMealRate?.toFixed(0) ?? "-"}%
            </p>
          )}
        </div>
      </div>

      {/* TOP3 */}
      <h2 className="section-title text-text-main mb-4">TOP3</h2>
      {top3.length === 0 ? (
        <div className="info-card py-8 text-center text-text-sub">아직 순위 데이터가 없습니다.</div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          {top3.map((entry) => {
            const Icon = RANK_ICONS[entry.rank];
            return (
              <div key={entry.memberId} className="info-card relative overflow-hidden">
                {Icon && (
                  <div className="absolute top-3 right-3 text-primary-500">
                    <Icon className="w-6 h-6" strokeWidth={2} />
                  </div>
                )}
                <div className="flex items-center gap-2 mb-2">
                  <span className="text-text-main font-bold text-lg">{entry.rank}등</span>
                  <span className="text-primary-500 text-sm font-medium">
                    {RANK_BADGE[entry.rank] ?? ""}
                  </span>
                </div>
                <p className="text-text-main font-display font-bold text-xl mb-1">{entry.memberName}</p>
                <p className="text-text-sub text-sm">{RANK_SUB[entry.rank] ?? ""}</p>
                {entry.combinedRate != null && (
                  <p className="text-primary-500 text-sm font-medium mt-1">
                    수행률 {entry.combinedRate.toFixed(1)}%
                  </p>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* 전체 순위 */}
      <h2 className="section-title text-text-main mb-4">전체 순위</h2>
      <div className="info-card">
        {fullList.length === 0 ? (
          <div className="py-8 text-center text-text-sub">순위 목록이 없습니다.</div>
        ) : (
          <ul className="divide-y divide-border-default">
            {fullList.map((entry) => (
              <li
                key={entry.memberId}
                className="py-3 flex items-center justify-between text-text-main"
              >
                <span className="font-medium">{entry.rank}위</span>
                <span className="font-display">{entry.memberName}</span>
                {entry.combinedRate != null && (
                  <span className="text-primary-500 text-sm">{entry.combinedRate.toFixed(1)}%</span>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

export default RankingList;
