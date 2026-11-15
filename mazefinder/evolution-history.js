// A bounded journal of immutable worker-owned route copies. Retention never changes the solver.
export class EvolutionHistory {
  constructor(limit = 256) {
    this.limit = Math.max(5, limit);
    this.events = [];
    this.largest = null;
  }
  append(incoming) {
    for (const event of incoming) {
      if (this.events.length && event.generation <= this.events.at(-1).generation) continue;
      const drop = event.previousFitness - event.fitness;
      if (!this.largest || drop > this.largest.previousFitness - this.largest.fitness) this.largest = event;
      this.events.push(event);
      while (this.events.length > this.limit) {
        const remove = this.events.findIndex((candidate, i) => i > 0 && i < this.events.length - 1
          && !candidate.firstSolution && !candidate.optimal && candidate !== this.largest);
        this.events.splice(remove, 1);
      }
    }
  }
  get(generation) { return this.events.find((event) => event.generation === generation); }
}

// Undirected edges let a route's changed geometry stand out without inventing mutation provenance.
export function changedEdges(previous, current) {
  const edges = (path) => {
    const result = new Map();
    for (let i = 2; i < path.length; i += 2) {
      const a = `${path[i - 2]},${path[i - 1]}`, b = `${path[i]},${path[i + 1]}`;
      if (a === b) continue;
      const key = a < b ? `${a}:${b}` : `${b}:${a}`;
      result.set(key, [path[i - 2], path[i - 1], path[i], path[i + 1]]);
    }
    return result;
  };
  const before = edges(previous), after = edges(current);
  return { added: [...after].filter(([key]) => !before.has(key)).map(([, edge]) => edge),
    removed: [...before].filter(([key]) => !after.has(key)).map(([, edge]) => edge) };
}
