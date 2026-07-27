---
layout: post
title: "Full Access, One Exception"
date: 2026-07-27
---

I spent a recent evening giving an AI assistant full, unsupervised access to my computer — terminal, filesystem, GitHub, my Google account, all of it. No permission prompts, no confirmations, nothing standing between "I asked for it" and "it happened." I wanted the same thing my team's internal tooling gives us at work: an assistant that doesn't stop to ask twice.

Setting that up turned into a smaller, more interesting problem than I expected: once you actually remove every guardrail, which one do you put back?

## The default answer is usually wrong

The easy version of "full access" is a single switch: bypass every check, trust every action, move fast. That's tempting, and for most of what I was doing — editing files, running builds, pushing branches — it was exactly right. Low stakes, fully reversible, git remembers everything anyway.

But partway through, my setup ended up connected to a handful of real external services, and one of them was a payment platform with an API call that creates and sends a real invoice to a real person, with no draft step and no way to unsend it. Under "bypass everything," that action had exactly the same status as "read a file." Same friction, same trust, wildly different consequences.

That's the moment a blanket policy stops being a policy and starts being an accident waiting for the wrong prompt.

## Least privilege, with named exceptions

The fix wasn't to walk back the whole "full access" decision — it was to keep it, and carve out explicit, narrow exceptions for the handful of actions where "irreversible" and "external" overlap. Concretely: everything stays frictionless by default, except a short, specific deny-list — the invoice-creation call, a couple of calendar actions that would silently modify or delete events on someone else's calendar. Those still require an actual human in the loop, no matter what mode the rest of the system is running in.

This is a pattern I recognize from data engineering, even though I landed on it by way of a side project: you don't put a review gate in front of every pipeline run, because most runs are safe, reversible, and cheap to redo. You put the gate in front of the specific operations where being wrong is expensive to undo — a schema migration against production, a delete against a table with no backup, a job that emails a customer. Everything else gets to move fast precisely because the dangerous 5% is fenced off by name, not by hoping the blanket policy holds.

The mistake I almost made was treating "convenience" and "safety" as one dial. They're not. Most of a system can be maximally convenient and still be safe, as long as you've actually gone looking for the handful of actions that aren't like the others — and you only find those by asking "what's the worst thing this could do right now," not by assuming the average case represents the whole distribution.

## Verify, don't assume

The other habit that paid off had nothing to do with permissions. Partway through some unrelated cleanup, I had a hunch that a mobile layout issue existed — a nav bar that might be overflowing on narrow screens. I could have "fixed" it based on the hunch. Instead I scripted a real headless browser, loaded the actual page at an actual phone width, and measured it: `document.documentElement.scrollWidth` against `clientWidth`. The gap was real — 50 pixels of horizontal overflow, invisible on a desktop screen, completely obvious once measured.

Two fixes later, I measured again. Zero overflow. That's the whole loop: don't trust that a fix worked because it looks reasonable in the code — trust the number you get from actually running it.

The same thing happened with a set of icon names I'd guessed at for a skills section. Reasonable-looking names, wrong on every count. Querying the actual icon registry instead of guessing again took thirty seconds and caught all three mistakes before they shipped.

Neither of these was a dramatic bug. Both were the kind of small, confidently-wrong assumption that's easy to ship if you don't check — the same category of mistake that shows up as a silently-broken dashboard or a pipeline that's been writing malformed rows for a week before anyone notices, just with lower stakes.

## The actual takeaway

None of this was really about AI tooling. It was a small, low-stakes rehearsal of a much more general engineering question: when you're building something that's supposed to run without a human watching every step, where does the human actually still need to be? Not everywhere — that's just a slower system that trusts itself less. Not nowhere — that's the invoice-API problem. Somewhere specific, chosen on purpose, and small enough that it doesn't get in the way of everything else.

That's the same question underneath a data pipeline's approval gates, a CI/CD deploy policy, or a Terraform plan that requires manual apply in prod but not in dev. Different domain, same shape.
