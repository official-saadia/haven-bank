const SYMBOLS: Record<string, string> = { GBP: "£", USD: "$", EUR: "€" };

/** Format a numeric amount with the account's currency symbol, always two decimals. */
export function formatMoney(amount: number, currency: string): string {
  const symbol = SYMBOLS[currency] ?? currency + " ";
  return symbol + amount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function accountLabel(type: string): string {
  switch (type) {
    case "CHECKING": return "Current";
    case "SAVINGS": return "Savings";
    default: return type;
  }
}
