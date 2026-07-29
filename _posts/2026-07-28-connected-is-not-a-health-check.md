---
layout: post
title: "Connected Is Not a Health Check"
date: 2026-07-28
---

I have five MCP servers running on my machine — small services that expose tools to an AI assistant, for chess stats, fantasy football, Reddit, Clash of Clans, and my Google account. Every one of them reports **Connected** in green text. That word turns out to be doing almost no work.

An MCP server can start cleanly, complete its handshake, sit there in the client UI looking perfectly healthy, and expose exactly nothing. Or expose a tool whose input schema doesn't compile, so no client can validate a call to it. Or list a required argument that isn't defined anywhere in its own schema, making every invocation of that tool fail for a reason no error message will ever mention. None of that throws. Nothing turns red. You just get worse answers and no idea why.

That gap — between "the process is up" and "the thing actually works" — is where I spent the weekend.

## The failure has no symptom

What makes this specific bug family nasty isn't that it's hard to fix. Every one of these faults is a two-line correction once you know it's there. It's that there's no moment where you find out.

A crashed server is easy mode. It's loud, it's obvious, and the stack trace tells you where to look. A server that starts fine and quietly advertises an empty tool list produces the same green dot as a server doing its job perfectly. The model just... doesn't call anything. And because these systems are non-deterministic anyway, "the assistant didn't use my tool" reads as normal variance rather than a defect. You shrug and rephrase your question. You do not think "my schema is malformed," because why would you.

So I wrote something to look: [mcp-surface](https://github.com/brettadams0/mcp-surface). It connects to a server the way a real client does, reads back the tool surface it actually exposes, and reports what's wrong with it. Fourteen checks — unsatisfiable required fields, schemas that don't compile, duplicate names where only one is reachable, tools with no schema at all. And it can snapshot that surface to a file you commit, so CI fails the day a refactor silently stops registering a tool.

## The part that surprised me

My first version was maybe forty lines. The official MCP SDK ships a client; the client has a `listTools()` method; call it, inspect what comes back, done.

It didn't work, and the reason is the most interesting thing I learned all weekend.

The SDK validates the response against a strict schema. If a server returns twelve tools and *one* of them is malformed, the SDK rejects the entire response. Not the bad tool — the whole list. So my checker, for any server actually worth checking, could only ever report one thing: *the response failed to parse.* Which tool? Couldn't say. What was wrong with it? No idea.

The library was protecting me from precisely the data I was trying to inspect.

The fix was to stop using the convenience method and issue the raw protocol request myself with a permissive schema — accept whatever the server sends, then apply my own judgment. That's what makes the difference between "this server is broken somehow" and "`unsatisfiable` lists a required argument called `apiKey` that its schema never defines." The first is a shrug. The second is a fix.

There's a corollary I didn't expect. The SDK validates *outgoing* responses too — so a server built on the TypeScript SDK physically cannot emit half of these faults. When I wrote a deliberately broken server to test against, the SDK refused to serve it, and I had to hand-write a raw JSON-RPC server to produce the bugs. Which reframes the whole tool: the audience isn't people using the TypeScript SDK. It's Python, Go, and hand-rolled servers, where nothing is standing between a typo and a client.

## What it found in my own code

Then I ran it against my five servers, which is the part where writing your own linter stops being fun.

No errors — the schemas are valid, the names are unique, nothing is structurally broken. But 64 of my 69 tools have input properties with **no descriptions**, which is to say every single tool that takes an argument at all. A model deciding whether to call `chess_get_games_by_month` gets three arguments named `username`, `year`, and `month`, and absolutely nothing telling it what format `month` wants. It'll guess. It'll usually guess right. "Usually" is doing a lot of load-bearing work in that sentence, and I'd never once thought about it, because nothing had ever failed loudly enough to make me look.

I also caught myself doing the thing I'd just written a tool to prevent. Two of my checks warned when a server had more than 40 tools, or when its definitions exceeded 16 KB. Reasonable numbers. Completely made up. I'd picked them because they felt right and then hardcoded them into a binary where they'd read as measurements. If anyone had asked "why 40?" the honest answer was "vibes," which is a bad look for a tool whose entire pitch is *stop assuming, go measure*. They're command-line flags now, with those values as defaults and the documentation saying plainly that they're heuristics rather than spec requirements.

## Where this actually generalizes

Strip the MCP specifics and this is a data quality problem wearing a different hat.

A pipeline that runs to completion and writes malformed rows looks identical, from the outside, to one that runs to completion and writes good ones. Exit code zero, green checkmark, job succeeded. The failure only surfaces days later when someone downstream notices their numbers are wrong, and by then you're not debugging a job — you're doing archaeology on a week of bad data. The fix isn't a better error message, because there was no error. It's a separate check that asserts on the *output* rather than the *process*, and that runs on a schedule rather than when something breaks, because nothing is going to break.

That's all mcp-surface is. It doesn't watch whether the server started. It looks at what the server produced and asks whether that thing is actually usable. Same as a schema test against a table, or a row-count assertion after a load, or a dbt test that fails a build because a column that should never be null suddenly is.

The uncomfortable version of this lesson: every system I've built has some surface where "it ran" and "it worked" are different questions, and I have historically only checked the first one, because the first one is the one that's easy to check. Success is not the absence of a stack trace. It's a claim, and claims want evidence.

Mine is on [npm](https://www.npmjs.com/package/mcp-surface) now, and it runs against my own servers in CI. It found something on the first try. That's usually a sign you should have looked sooner.
