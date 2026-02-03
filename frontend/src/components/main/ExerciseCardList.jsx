export default function ExerciseCardList({ routine, selectedExercise, onExerciseSelect }) {
  const exercises = routine?.exercises || [];

  if (exercises.length === 0) {
    return (
      <div className="w-full">
        <h2 className="text-lg font-bold text-text-main mb-4">운동 목록</h2>
        <div className="text-center py-8">
          <p className="text-text-muted">등록된 운동이 없습니다.</p>
        </div>
      </div>
    );
  }

  const handleExerciseClick = (exercise) => {
    if (onExerciseSelect) {
      onExerciseSelect(exercise);
    }
  };

  return (
    <div className="w-full">
      <h2 className="text-lg font-bold text-text-main mb-4">운동 목록</h2>
      <div className="overflow-x-auto pb-4">
        <div className="flex gap-4 min-w-max">
          {exercises.map((exercise, index) => {
            const isSelected = selectedExercise && selectedExercise.id === exercise.id;
            const isCompleted = exercise.completed;

            return (
              <div
                key={exercise.id || index}
                onClick={() => handleExerciseClick(exercise)}
                className={`flex-shrink-0 w-64 bg-bg-surface rounded-token p-4 border-2 cursor-pointer transition-all ${
                  isSelected
                    ? 'border-primary-500 shadow-lg shadow-primary-500/20'
                    : 'border-border-default hover:border-primary-500/50'
                }`}
              >
                {/* Body Part */}
                <div className="text-xs text-text-muted mb-2">
                  {exercise.mainTarget || 'Chest'}
                </div>

                {/* Exercise Name */}
                <h3 className={`text-lg font-bold mb-3 ${
                  isCompleted ? 'line-through text-text-muted' : 'text-text-main'
                }`}>
                  {exercise.name}
                </h3>

                {/* Sets and Reps */}
                <div className="text-sm text-text-main">
                  {exercise.sets} Sets - {exercise.reps} Reps
                  {exercise.weight && ` - ${exercise.weight}kg`}
                </div>

                {/* Completed Badge */}
                {isCompleted && (
                  <div className="mt-2 text-xs text-primary-500 font-medium">
                    완료
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

