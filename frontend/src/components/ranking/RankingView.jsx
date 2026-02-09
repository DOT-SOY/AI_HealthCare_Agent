import { useEffect, useMemo, useState } from "react";
import { rankingApi } from "../../api/rankingApi";

const ageGroupLabel = (code) => {
  if (code === "10s") return "20대 미만";
  if (code === "20s") return "20대";
  if (code === "30s") return "30대";
  if (code === "40s") return "40대";
  if (code === "50s") return "50대";
  if (code === "60plus") return "60대 이상";
  return "-";
};

const genderLabel = (code) => {
  if (code === "MALE") return "남성";
  if (code === "FEMALE") return "여성";
  return "-";
};

const purposeLabel = (code) => {
  if (code === "DIET") return "다이어트";
  if (code === "MAINTAIN") return "유지";
  if (code === "BULK_UP") return "벌크업";
  return "-";
};

function RankingView() {
  const [rankingData, setRankingData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchRanking = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await rankingApi.getRanking({ limit: 10 });
      setRankingData(data);
    } catch (err) {
      console.error("랭킹 조회 실패:", err);
      setError("랭킹 데이터를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRanking();
  }, []);

  const totalCount = useMemo(() => {
    const count = rankingData?.totalCount ?? 0;
    return count;
  }, [rankingData]);

  const myRankSummary = useMemo(() => {
    if (!rankingData || !rankingData.myScore) {
      return "";
    }
    const { myScore } = rankingData;
    return `${myScore.rank}위 / 총 ${totalCount}명`;
  }, [rankingData, totalCount]);

  const filterSummary = useMemo(() => {
    const filter = rankingData?.filterInfo;
    if (!filter) return "";

    const genderText = genderLabel(filter.gender);
    const ageText = ageGroupLabel(filter.ageGroup);
    const purposeText = purposeLabel(filter.exercisePurpose);

    return [genderText, ageText, purposeText].filter(Boolean).join(" · ");
  }, [rankingData]);

  const top3 = useMemo(
    () => (rankingData?.topRanks ? rankingData.topRanks.slice(0, 3) : []),
    [rankingData]
  );

  const restRanks = useMemo(
    () => (rankingData?.topRanks ? rankingData.topRanks.slice(3) : []),
    [rankingData]
  );

  return (
    <div className="w-full text-text-main font-sans">
      {/* 상단 헤더 */}
      <header className="section-header-token flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4 mb-6">
        <div>
          <h1 className="section-title">
            <span className="text-text-main">Today's </span>
            <span className="text-primary-500">Rank</span>
          </h1>
          <p className="section-desc mt-1">
            최근 7일 기준 · {filterSummary || "내 프로필(성별·나이대·운동 목적)과 같은 그룹 내 순위"}
          </p>
        </div>
      </header>

      {loading && (
        <p className="text-sm text-text-muted mb-4">랭킹 데이터를 불러오는 중입니다...</p>
      )}
      {error && <p className="text-sm text-red-500 mb-4">{error}</p>}

      {/* 1. 상단: 내 그룹 / 내 순위 박스 */}
      <section className="grid gap-4 lg:grid-cols-[minmax(0,1.5fr)_minmax(0,2fr)] mb-8">
        {/* 내 그룹 카드 */}
        <div className="card-token rounded-token px-6 py-5 relative overflow-hidden border-border-default">
          <div className="absolute inset-0 pointer-events-none opacity-40">
            <div className="h-32 w-32 rounded-full bg-primary-500/10 blur-3xl -top-10 -left-10 absolute" />
            <div className="h-32 w-32 rounded-full bg-accent-secondary/10 blur-3xl -bottom-16 -right-8 absolute" />
          </div>

          <div className="relative z-10">
            <p className="text-sm sm:text-base font-semibold tracking-[0.25em] uppercase text-primary-400 mb-2">
              내 그룹
            </p>
            <p className="text-lg sm:text-xl font-semibold mb-3">
              {filterSummary || "그룹 정보를 불러오는 중입니다."}
            </p>

            <div className="flex flex-wrap gap-2 text-[11px]">
              <span className="px-3 py-1 rounded-full bg-primary-500/15 text-primary-300 border border-primary-500/40">
                {genderLabel(rankingData?.filterInfo?.gender)}
              </span>
              <span className="px-3 py-1 rounded-full bg-primary-500/10 text-primary-300 border border-primary-500/30">
                {ageGroupLabel(rankingData?.filterInfo?.ageGroup)}
              </span>
              <span className="px-3 py-1 rounded-full bg-accent-secondary/10 text-accent-secondary border border-accent-secondary/40">
                {purposeLabel(rankingData?.filterInfo?.exercisePurpose)}
              </span>
            </div>

            <p className="mt-4 text-xs text-text-muted">
              같은 신체 조건과 운동 목적을 가진 사용자들과 경쟁 중입니다.
            </p>
          </div>
        </div>

        {/* 내 순위 카드 */}
        <div className="card-token rounded-token px-6 py-5 border-border-default flex flex-col justify-between">
          <div>
            <p className="text-sm sm:text-base font-semibold tracking-[0.25em] uppercase text-primary-400 mb-2">
              내 순위
            </p>
            <div className="flex items-center justify-between gap-4 mb-2">
              <div className="flex flex-col">
                <span className="text-5xl font-bold text-text-main mb-4">
                  {rankingData?.myScore ? rankingData.myScore.rank : "-"}
                </span>
                {myRankSummary && (
                  <span className="mt-1 text-sm sm:text-base text-text-muted">
                    {myRankSummary}
                  </span>
                )}
              </div>

              {rankingData?.myScore && (
                <div className="grid grid-cols-3 gap-3 text-center text-xs sm:text-sm min-w-[400px]">
                  <div className="bg-bg-root/60 border border-border-default rounded-xl py-2 px-3">
                    <p className="text-text-muted text-[11px] mb-0.5">식단</p>
                    <p className="text-primary-400 text-base sm:text-lg font-bold">
                      {rankingData.myScore.mealScore.toFixed(1)}%
                    </p>
                  </div>
                  <div className="bg-bg-root/60 border border-border-default rounded-xl py-2 px-3">
                    <p className="text-text-muted text-[11px] mb-0.5">운동</p>
                    <p className="text-primary-400 text-base sm:text-lg font-bold">
                      {rankingData.myScore.routineScore.toFixed(1)}%
                    </p>
                  </div>
                  <div className="bg-bg-root/60 border border-border-default rounded-xl py-2 px-3">
                    <p className="text-text-muted text-[11px] mb-0.5">종합</p>
                    <p className="text-accent-secondary text-base sm:text-lg font-bold">
                      {rankingData.myScore.totalScore.toFixed(1)}점
                    </p>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* 2. TOP 3 섹션 */}
      <section className="mb-8">
        <div className="flex items-center justify-between mb-3">
          <p className="text-sm sm:text-lg font-semibold tracking-[0.25em] uppercase text-primary-400">
            TOP 3
          </p>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          {top3.length === 0 ? (
            <div className="col-span-3 card-token rounded-token border-border-default px-6 py-6 text-center text-text-muted">
              아직 TOP 3 데이터가 없습니다.
            </div>
          ) : (
            top3.map((row) => (
              <div
                key={row.memberId}
                className="card-token rounded-token border-border-default px-6 py-6 flex flex-col justify-between"
              >
                <div className="flex-1 flex flex-col justify-center">
                  <p className="text-3xl font-bold text-text-main mb-4">
                    {row.rank}위
                  </p>
                  <p className="text-xl font-semibold text-text-main mb-1">{row.nickname}</p>
                </div>
                <div className="mt-4 flex justify-between text-xs text-text-muted">
                  <span>식단 {row.mealScore.toFixed(1)}%</span>
                  <span>운동 {row.routineScore.toFixed(1)}%</span>
                  <span className="text-primary-400 font-semibold">
                    총점 {row.totalScore.toFixed(1)}점
                  </span>
                </div>
              </div>
            ))
          )}
        </div>
      </section>

      {/* 3. 전체 순위 리스트 */}
      <section className="mb-10">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-base sm:text-lg font-bold text-text-main">전체 순위</h2>
        </div>
        <div className="space-y-2">
          {rankingData?.topRanks && rankingData.topRanks.length > 0 ? (
            rankingData.topRanks.map((row) => (
              <div
                key={row.memberId}
                className={`bg-bg-surface border border-border-default rounded-2xl px-4 py-3 flex items-center justify-between gap-3 ${
                  rankingData?.myScore?.memberId === row.memberId ? "ring-2 ring-primary-500/70" : ""
                }`}
              >
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 rounded-2xl bg-primary-500/20 flex items-center justify-center text-2xl font-extrabold text-primary-300">
                    {row.rank}
                  </div>
                  <div>
                    <p className="text-m font-semibold text-text-main">{row.nickname}</p>
                  </div>
                </div>
                <div className="flex items-center gap-4 text-[11px] sm:text-xs text-text-muted">
                  <div className="text-right">
                    <p>식단</p>
                    <p className="text-primary-400 font-semibold">
                      {row.mealScore.toFixed(1)}%
                    </p>
                  </div>
                  <div className="text-right">
                    <p>운동</p>
                    <p className="text-primary-400 font-semibold">
                      {row.routineScore.toFixed(1)}%
                    </p>
                  </div>
                  <div className="text-right">
                    <p>총점</p>
                    <p className="text-accent-secondary font-bold">
                      {row.totalScore.toFixed(1)}점
                    </p>
                  </div>
                </div>
              </div>
            ))
          ) : (
            <div className="bg-bg-surface border border-border-default rounded-2xl px-4 py-6 text-center text-text-muted">
              전체 순위 데이터가 없습니다.
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

export default RankingView;



