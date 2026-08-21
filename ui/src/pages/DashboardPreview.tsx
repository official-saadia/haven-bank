/** Static design preview of the dashboard (mock data, no backend) - used for review/screenshots. */
const accounts = [
  { name: "Current", kind: "active", number: "••4821", balance: "£4,238.65" },
  { name: "Savings", kind: "active", number: "••7390", balance: "£12,050.00" },
];
const activity = [
  { date: "24 Jul", desc: "transfer · TXN-9F2A1C7B", amount: "-£120.00", credit: false },
  { date: "23 Jul", desc: "deposit · TXN-1A77E0D3", amount: "+£2,400.00", credit: true },
  { date: "22 Jul", desc: "transfer · TXN-4C09B215", amount: "-£54.30", credit: false },
  { date: "21 Jul", desc: "transfer · TXN-77B3EE10", amount: "-£950.00", credit: false },
  { date: "20 Jul", desc: "deposit · TXN-2D5F8A94", amount: "+£18.40", credit: true },
];

export function DashboardPreview() {
  return (
    <section className="dash">
      <header className="dash__head">
        <div>
          <h1 className="dash__heading">Accounts</h1>
          <p className="dash__total">£16,288<span className="dash__cents">.65</span></p>
          <p className="dash__totalnote">Total across 2 accounts</p>
        </div>
        <button className="btn btn--primary">Move Money</button>
      </header>
      <div className="accounts">
        {accounts.map((a) => (
          <article key={a.number} className="acct">
            <div className="acct__top"><span className="acct__name">{a.name}</span><span className="acct__kind">{a.kind}</span></div>
            <span className="acct__num mono">{a.number}</span>
            <span className="acct__bal mono">{a.balance}</span>
          </article>
        ))}
      </div>
      <section className="ledger">
        <div className="ledger__head"><h2>Recent Activity</h2><a href="#">View All</a></div>
        <div className="ledger__rows">
          {activity.map((t, i) => (
            <div className="ledger__row" key={i}>
              <span className="mono ledger__date">{t.date}</span>
              <span className="ledger__desc">{t.desc}</span>
              <span className={"mono ledger__amt" + (t.credit ? " is-credit" : "")}>{t.amount}</span>
            </div>
          ))}
        </div>
      </section>
    </section>
  );
}
