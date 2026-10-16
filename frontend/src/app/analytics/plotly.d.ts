declare const Plotly: {
  newPlot(element: HTMLElement, data: unknown[], layout?: Record<string, unknown>, config?: Record<string, unknown>): Promise<unknown>;
  purge(element: HTMLElement): void;
};
