export default function AISummaryCard({ routine }) {
  // 실제 AI summary가 있으면 사용, 없으면 기본 메시지
  const summaryText = routine?.summary || 'AI 코칭 요약이 없습니다.';
  
  // summary를 줄바꿈 기준으로 분리
  const summaryPoints = summaryText.split('\n').filter(line => line.trim());

  return (
    <div className="card-token rounded-token p-6 mb-6 border border-border-default">
      <div className="flex items-center gap-3 mb-4">
        <div className="w-8 h-8 flex items-center justify-center">
          <span className="text-xl text-primary-500">☀️</span>
        </div>
        <h2 className="text-xl font-semibold text-text-main">AI 코칭 요약</h2>
      </div>
      {summaryPoints.length > 0 ? (
        <ul className="space-y-2">
          {summaryPoints.map((point, index) => (
            <li key={index} className="text-text-sub flex items-start gap-2">
              <span className="mt-1 text-primary-500">•</span>
              <span>{point.trim()}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-text-muted">AI 코칭 요약이 없습니다.</p>
      )}
    </div>
  );
}


