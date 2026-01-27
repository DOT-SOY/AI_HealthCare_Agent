const QtyStepper = ({ value, onChange, min = 1, disabled = false }) => {
  const handleDecrease = () => {
    if (value > min && !disabled) {
      onChange(value - 1);
    }
  };

  const handleIncrease = () => {
    if (!disabled) {
      onChange(value + 1);
    }
  };

  return (
    <div className="flex items-center gap-2">
      <button
        type="button"
        onClick={handleDecrease}
        disabled={value <= min || disabled}
        className="w-8 h-8 flex items-center justify-center border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed transition"
      >
        −
      </button>
      <span className="w-12 text-center font-medium">{value}</span>
      <button
        type="button"
        onClick={handleIncrease}
        disabled={disabled}
        className="w-8 h-8 flex items-center justify-center border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed transition"
      >
        +
      </button>
    </div>
  );
};

export default QtyStepper;
