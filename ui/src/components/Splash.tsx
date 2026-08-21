/** Full-height centered status message used during auth transitions. */
export function Splash({ label }: { label: string }) {
  return (
    <div className="splash">
      <span className="splash__mark">Haven Bank</span>
      <p className="splash__label">{label}…</p>
    </div>
  );
}
