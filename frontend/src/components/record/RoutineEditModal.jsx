import { useState, useEffect } from 'react';

export default function RoutineEditModal({ routine, isOpen, onClose, onSave }) {
  const [formData, setFormData] = useState({
    title: '',
    exercises: [],
  });

  useEffect(() => {
    if (routine) {
      setFormData({
        title: routine.title || '',
        exercises: routine.exercises || [],
      });
    }
  }, [routine]);

  if (!isOpen) return null;

  const handleExerciseChange = (index, field, value) => {
    const updatedExercises = [...formData.exercises];
    updatedExercises[index] = {
      ...updatedExercises[index],
      [field]: field === 'sets' || field === 'weight' ? parseFloat(value) || 0 : value,
    };
    setFormData({ ...formData, exercises: updatedExercises });
  };

  const handleAddExercise = () => {
    setFormData({
      ...formData,
      exercises: [
        ...formData.exercises,
        { name: '', sets: 0, reps: '', weight: 0 },
      ],
    });
  };

  const handleRemoveExercise = (index) => {
    const updatedExercises = formData.exercises.filter((_, i) => i !== index);
    setFormData({ ...formData, exercises: updatedExercises });
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onSave(formData);
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 overflow-y-auto">
      <div className="bg-neutral-800 rounded-lg p-6 w-full max-w-2xl my-8">
        <h2 className="text-xl font-bold text-neutral-50 mb-4">루틴 수정</h2>
        
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-neutral-300 mb-1">루틴 제목</label>
            <input
              type="text"
              value={formData.title}
              onChange={(e) => setFormData({ ...formData, title: e.target.value })}
              className="w-full bg-neutral-700 text-neutral-50 px-4 py-2 rounded-lg focus:outline-none focus:ring-2 focus:ring-neon-green"
              required
            />
          </div>

          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="block text-sm font-medium text-neutral-300">운동 목록</label>
              <button
                type="button"
                onClick={handleAddExercise}
                className="px-3 py-1 bg-neon-green text-neutral-950 rounded-lg text-sm font-medium hover:bg-neon-green/80"
              >
                + 운동 추가
              </button>
            </div>

            <div className="space-y-3 max-h-96 overflow-y-auto">
              {formData.exercises.map((exercise, index) => (
                <div key={index} className="bg-neutral-700 rounded-lg p-4">
                  <div className="flex items-start justify-between mb-3">
                    <span className="text-neutral-300 text-sm">운동 {index + 1}</span>
                    <button
                      type="button"
                      onClick={() => handleRemoveExercise(index)}
                      className="text-red-400 hover:text-red-300 text-sm"
                    >
                      삭제
                    </button>
                  </div>

                  <div className="grid grid-cols-2 gap-3 mb-3">
                    <div>
                      <label className="block text-xs text-neutral-400 mb-1">운동명</label>
                      <input
                        type="text"
                        value={exercise.name || ''}
                        onChange={(e) => handleExerciseChange(index, 'name', e.target.value)}
                        className="w-full bg-neutral-600 text-neutral-50 px-3 py-2 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neon-green"
                        required
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-3 gap-3">
                    <div>
                      <label className="block text-xs text-neutral-400 mb-1">세트</label>
                      <input
                        type="number"
                        value={exercise.sets || 0}
                        onChange={(e) => handleExerciseChange(index, 'sets', e.target.value)}
                        className="w-full bg-neutral-600 text-neutral-50 px-3 py-2 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neon-green"
                        min="0"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-xs text-neutral-400 mb-1">횟수</label>
                      <input
                        type="text"
                        value={exercise.reps || ''}
                        onChange={(e) => handleExerciseChange(index, 'reps', e.target.value)}
                        className="w-full bg-neutral-600 text-neutral-50 px-3 py-2 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neon-green"
                        placeholder="10-12"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-xs text-neutral-400 mb-1">무게(kg)</label>
                      <input
                        type="number"
                        value={exercise.weight || 0}
                        onChange={(e) => handleExerciseChange(index, 'weight', e.target.value)}
                        className="w-full bg-neutral-600 text-neutral-50 px-3 py-2 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neon-green"
                        min="0"
                        step="0.5"
                        required
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="flex gap-2 justify-end mt-6">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 bg-neutral-700 text-neutral-300 rounded-lg hover:bg-neutral-600 transition-colors"
            >
              취소
            </button>
            <button
              type="submit"
              className="px-4 py-2 bg-neon-green text-neutral-950 rounded-lg hover:bg-neon-green/80 transition-colors font-medium"
            >
              저장
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}


