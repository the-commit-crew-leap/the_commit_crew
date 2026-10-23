const marketTimeZone = 'America/New_York';

export function isUsMarketOpen(date = new Date()): boolean {
    const parts = new Intl.DateTimeFormat('en-US', {
        timeZone: marketTimeZone,
        weekday: 'short',
        hour: 'numeric',
        minute: 'numeric',
        hour12: false
    }).formatToParts(date);
    const values = Object.fromEntries(parts.map(({ type, value }) => [type, value]));
    const weekday = values['weekday'];
    const minutes = Number(values['hour']) * 60 + Number(values['minute']);

    return weekday !== 'Sat'
        && weekday !== 'Sun'
        && minutes >= 9 * 60 + 30
        && minutes < 16 * 60;
}
