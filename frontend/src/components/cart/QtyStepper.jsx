const QtyStepper = ({
  value,
  onChange,
  min = 1,
  disabled = false,
  className = '',
  buttonClassName = 'border-gray-300 hover:bg-gray-100',
  valueClassName = '',
}) => {
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

  const btnBase =
    'w-8 h-8 flex items-center justify-center border rounded disabled:opacity-50 disabled:cursor-not-allowed transition';

  return (
    <div className={`flex items-center gap-2 ${className}`.trim()}>
      <button
        type="button"
        onClick={handleDecrease}
        disabled={value <= min || disabled}
        className={`${btnBase} ${buttonClassName}`.trim()}
      >
        −
      </button>
      <span className={`w-12 text-center font-medium ${valueClassName}`.trim()}>{value}</span>
      <button
        type="button"
        onClick={handleIncrease}
        disabled={disabled}
        className={`${btnBase} ${buttonClassName}`.trim()}
      >
        +
      </button>
    </div>
  );
};

export default QtyStepper;
