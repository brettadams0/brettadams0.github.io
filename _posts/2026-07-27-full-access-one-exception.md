---
layout: post
title: "Full Access, One Exception"
date: 2026-07-27
---

I recently gave an AI assistant full, unsupervised access to my computer. Terminal, filesystem, GitHub, my Google account — the whole thing, no permission prompts, nothing standing between "I asked for it" and "it happened." In hindsight, this is the exact plot of every movie where that goes badly. I did it anyway, because my team runs on internal tooling that works the same way, and I wanted to know what it actually takes to build that trust from scratch instead of just enjoying it at work like a tourist.

It turned into a better problem than I expected. Once you genuinely remove every guardrail, the interesting question isn't "was that reckless" — obviously — it's: which one do you quietly put back before it costs you something you can't take back?

## The default answer is usually wrong

The lazy version of "full access" is one switch: bypass every check, trust every action, don't look back. For most of what I was actually doing — editing files, running builds, pushing branches — that switch was correct. Low stakes, fully reversible, and git has the memory of an elephant that never forgives you but never forgets your work either.

Then the setup grew a few real external connections, and one of them was a payments API with a call that creates *and sends* an invoice to a real person — no draft, no undo, no "are you sure." Under "bypass everything," that call had the exact same status as reading a text file. Same zero friction, wildly different ability to ruin someone's afternoon.

That's the exact moment a blanket policy stops being a policy and becomes a story I'd have to tell people afterward, starting with "so, funny thing."

## Least privilege, with named exceptions

I didn't walk back the full-access decision. I kept it, and carved out narrow, named exceptions for the specific actions where "irreversible" and "external" overlap — the invoice call, a couple of calendar actions that could silently edit or delete something on someone else's calendar without them ever knowing I was the one who did it. Everything else stays instant. Those specific few still need an actual human to say yes, no matter what mode the rest of the system is running in.

I recognize this pattern from data engineering, even though I backed into it sideways through a personal project at 11pm. You don't put a review gate in front of every pipeline run — most runs are safe, cheap, and trivially re-runnable. You put the gate in front of the operations where being wrong is expensive to undo: a schema migration against prod, a delete against a table with no backup, a job that emails a customer something you can't unsend. Everything else gets to move fast precisely *because* the dangerous handful is fenced off by name, not because you're hoping the blanket rule holds up under pressure.

The mistake I almost made was treating "convenient" and "safe" as one dial, where turning one down turns the other up. They're not the same dial. Most of a system can be maximally convenient and still be genuinely safe, as long as you've gone looking, on purpose, for the small set of actions that aren't like the others. You find those by asking "what's the actual worst thing this specific button does," not by assuming the typical case speaks for the whole distribution. I did not think of this line while panicking about the invoice API. I thought of it afterward, in the shower, like a normal well-adjusted engineer.

## Verify, don't assume

The other habit that earned its keep had nothing to do with permissions. I had a hunch, elsewhere in the same project, that something was overflowing on mobile — a nav bar that looked like it might be a little too proud of itself on a small screen. I could have "fixed" it based on vibes and moved on with my life. Instead I scripted a real headless browser, loaded the real page at a real phone width, and measured it directly: `document.documentElement.scrollWidth` against `clientWidth`. Fifty pixels of horizontal overflow, invisible on my desktop monitor, completely undeniable the moment I actually measured instead of guessed.

Two fixes later, I measured again. Zero. That's the entire loop, and it's not complicated: don't trust that a fix worked because the diff looks reasonable — trust the number that comes out when you actually run it.

The same thing happened, more embarrassingly, with a set of icon names I'd confidently typed from memory for a skills section. Every single one was wrong. Not almost-right — wrong, the kind of wrong that renders as a blank square and a shrug. Actually querying the icon registry instead of guessing a second time took about thirty seconds and caught all three before anyone but me ever saw them.

Neither of these was a dramatic, portfolio-story bug. Both were the small, confidently-wrong kind of assumption that ships fine right up until it doesn't — the same species of mistake that shows up later as a quietly broken dashboard, or a pipeline that's been writing malformed rows for a week because nobody happened to look. Lower stakes here. Same shape.

## The actual takeaway

This was never really about AI tooling. It was a cheap, low-stakes rehearsal of a much bigger engineering question: when you build something meant to run without a human watching every step, where does the human still need to stand? Not everywhere — that's just a slower system that's decided not to trust itself. Not nowhere — ask the invoice API how that goes. Somewhere specific, chosen on purpose, small enough that it never gets in the way of everything else you've earned the right to automate.

It's the same question sitting underneath a data pipeline's approval gates, a CI/CD deploy policy, or a Terraform plan that demands a human `apply` in prod but not in dev. Different costume, same question. I just happened to meet it first while giving a piece of software the keys to my email.
