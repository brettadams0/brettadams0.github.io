---
layout: post
title: "The Simulator That Never Evolved"
date: 2026-10-16
---

[neuronet-arena](https://github.com/brettadams0/neuronet-arena) has been on my GitHub since April 2025. It's an evolution simulator: twenty agents wander a 2D arena of food and hazards, each one steered by a small neural network I wrote by hand, and at the end of every generation the best performers are cloned, mutated, and sent back in. The README ended with a list of things it could do next, and the last item was "a fitness-over-time plot, which would show whether evolution is actually working or just drifting."

That turned out to be the most important sentence in the repository. Evolution wasn't working. It wasn't drifting either. It wasn't happening at all.

## Every generation was generation zero

The generation loop looked like this:

```python
for gen in range(self.generations):
    self.setup()
    for step in range(self.steps_per_generation):
        self.update_agents()
        self.visualizer.draw(step, gen)
    self.evolve_agents()
```

`evolve_agents()` did its job: rank the agents, clone the best brains, mutate them, store the new population. Then the loop came back around and `setup()` built twenty brand-new agents with twenty brand-new random brains, and the evolved ones were never seen again. When I instrumented it, the number of evolved brains that made it into the next generation was zero out of twenty, every generation.

There was a second bug underneath the first. Food was never consumed. An agent touching a food node scored a point per tick, for as long as it stayed there, so the theoretically optimal agent was one that found food and stopped moving. I checked: an agent parked on a single food node scored 300 in 300 ticks.

Neither bug was visible in the window. It rendered, agents wandered around, some of them blundered into food and some into hazards, the generation counter ticked up. Random agents in a world full of targets look a lot like agents that are learning, and nothing on screen says that every generation is a fresh roll of the dice.

Which is the [same lesson as my last post](/blog/2026/07/28/connected-is-not-a-health-check/), arriving from a different direction: the process ran to completion every single time. What it produced was nothing.

## Fixing it is easy. Knowing it's fixed isn't

Deleting one line fixes the first bug. The harder question is how you'd know. A curve that goes up is not proof of anything in a stochastic system: one lucky seed can do it, and so can a fitness function that rewards something other than what you meant.

So I rewrote the thing to be measured. The engine is now a headless, seeded Python package, and `neuronet experiment` runs every seed twice with identical settings. The first run is normal. The second has selection switched off: parents are drawn at random, nothing is kept as elite, and fitness is computed but never read. Both runs start from the same population in the same world, so any gap that opens between them is selection's doing and nothing else's.

Over 8 seeds and 150 generations, the populations with selection went from 1.07 food per agent to **13.39**. The ones without it ended at **0.98**. The range across seeds was 12.75 to 13.76 with selection, and 0.59 to 1.49 without. Hazard avoidance came along for free: nothing rewards it directly, but touching a hazard costs energy, energy is survival, and survival is time to eat. Evolved agents spent 0.52 steps touching a hazard per food eaten, while the control spent 3.56.

That plot, the one the old README asked for, would have caught the original bug on its first run. It would have been a flat line.

## Putting it in a browser

A simulator is a much better portfolio piece when you can watch it, so I wanted a live demo. The obvious move was to port the logic to JavaScript and call it done. But a port that merely behaves *similarly* is a different program that happens to share a name, and every number above is a claim about the Python one. I wanted the demo to be the same simulation, provably.

That is harder than it sounds, and the reason surprised me.

IEEE 754 requires addition, subtraction, multiplication, division and square root to be correctly rounded, so those give identical results on every conforming platform. It makes no such demand of `tanh`, `exp`, `sin` or `cos`. I ran 200,000 random inputs through both CPython 3.13 and Node 22, and they returned different doubles for `tanh` 62 times, for `sin` 6,690 times and for `exp` 19,930 times. Sixty-two in 200,000 sounds negligible until you count the calls. A generation runs up to 288,000 activations, and a 600-step simulation full of agents competing for the same food is chaotic, so one flipped last bit early on is a different run by the end.

So the engine only uses the operations IEEE pins down:

- **Softsign instead of tanh.** `x / (1 + |x|)` has the same S-shape and needs nothing but an add, an abs and a divide.
- **Turning without angles.** Heading is a unit vector, and a turn is a Cayley rotation, `cos θ = (1 − t²)/(1 + t²)` and `sin θ = 2t/(1 + t²)`. That is an exact rotation built from arithmetic alone.
- **A random number generator I own.** Python's Mersenne Twister has no JavaScript twin, so both engines carry the same xoshiro128\*\*, in 32-bit integer arithmetic that both languages do exactly.
- **No `sum()`.** This one is easy to miss. Since Python 3.12, `sum()` over floats uses compensated summation. It's more accurate, and therefore no longer equal to a plain loop in JavaScript. The hot path uses explicit loops in both languages.

To check it, the Python engine writes a golden trace: RNG output, a forward pass, and a four-generation run sampled every 20 steps, with positions, headings and energy included. Both test suites compare against it with exact equality, not tolerance. It reproduces identically on every Python from 3.10 to 3.14.

The demo also checks itself. It starts from a population I trained in Python for 200 generations, saved alongside the stats Python recorded for generation 200. When your browser finishes replaying that generation, a panel compares the two results field by field. It says *identical*. If it ever says anything else, one of the engines has a bug, and the page will tell you before I find out.

Making it exact paid for itself. A generation takes about 0.7 seconds in CPython and under 20 milliseconds in V8, so the browser can fast-forward through dozens of generations a second while the Python package stays the reference implementation.

## What I'd tell myself in April 2025

Make the claim measurable before you make it impressive. The original project's pitch was that nothing in it was a library call. That was true, and it was also beside the point, because nothing in it worked either. The most valuable code in the rewrite isn't the network or the genetic algorithm. It's the control experiment, a couple of dozen lines that run the same thing twice with one switch flipped, and the golden trace, which is a JSON file. Neither one is clever, but they are what let me say the thing works and point at the evidence.

[The demo is here](/neuronet-arena/). It opens on the trained population at normal speed. Switch to **From scratch** and hit **Max**, and forty random networks go from aimless to foraging in a few seconds. Then drop back to 1× and watch what they learned.
