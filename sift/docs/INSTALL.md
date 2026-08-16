# Installing Sift

Sideload only. Sift is not on the Play Store and never will be (§17) — it needs
`READ_MEDIA_IMAGES` over your whole camera roll, which is exactly the permission
Play distribution makes painful, and there is nobody to distribute it to.

---

## 1. Put the APK on the phone

Open this link in the phone's browser and tap download:

**https://github.com/brettadams0/sift/raw/main/dist/sift-0.2.0-release.apk**

Or take it from the **Actions** tab of this repo — any green `Android build` run
carries it as the `sift-release-apk` artifact — or build it yourself with
`./gradlew assembleRelease`.

If you have `adb`:

```sh
adb install -r sift-0.2.0-release.apk
```

`-r` matters: it reinstalls over an existing copy and **keeps your database**,
so pending deletions and photos awaiting review survive the upgrade. Every build
is signed with the same key, so this works from any earlier version. Never
uninstall to get around a signature error — that erases the queues along with
the app.

## 2. Allow the install

Samsung will block it the first time. When the "unsafe app blocked" prompt
appears: **Settings → Apps → Special access → Install unknown apps →** whichever
app you opened the APK with (Files, Chrome, Drive) → allow.

## 3. Grant permissions

On first launch Sift asks for three things and explains each on screen:

| Permission | Why |
|---|---|
| Photos and videos | The camera roll it triages. Without it there is no deck. |
| **Location (media)** | **Keeps GPS in your exports.** Android silently strips GPS from every photo handed to an app that lacks this, and you would not notice for months (trap #12). |
| Notifications | The progress notification for background grading (§9.2). |

There is no network permission to grant — Sift ships without one (§3), which you
can confirm yourself under **Settings → Apps → Sift → Permissions**.

## 4. Point Google Photos at the exports

Sift writes graded photos to `Pictures/Sift`. It cannot upload them itself and
deliberately has no way to, so if you want them in the cloud, let Google Photos
pick the folder up:

**Google Photos → Settings → Backup → Back up device folders → `Pictures/Sift` → on.**

This is also the answer to why Sift cannot delete your cloud copies: Google
removed the Library API scope that would allow it in April 2025, so the API can
only touch content the app itself created. Trashing in Sift frees space on the
device; the cloud copy is yours to manage (§1).

---

## First run

1. **Wait for the first scan.** Sift pages your library in, hashes it and runs
   the analysis pass in the background (§7). The grid works immediately; burst
   clustering and screenshot detection fill in behind it. On a large library this
   takes a few minutes — it is deliberately low priority so it never blocks the
   deck.

2. **Triage.** Swipe right to keep, left to toss, up to skip. Or use the volume
   keys — **vol-up keeps, vol-down tosses** — which is faster and lets your thumb
   rest (§8). Long-press any frame for 1:1 zoom when you are unsure whether it is
   sharp. Undo covers the last ten decisions.

3. **Nothing is deleted while you swipe.** Every decision is written to the
   database and nothing else. Tap **Commit** and all your rejects go into one
   system trash dialog (§8). Cancel it and every decision is still there — you
   will not have to re-triage.

   If you tossed something by accident and only notice later, open **Pending
   deletions** from the home screen: every queued photo is there as a thumbnail
   with a ↩ to pull that one back out. Undo covers the last ten decisions in
   order; this covers any of them, at any time before you commit.

4. **Keepers get graded automatically** once you commit, if the battery is above
   30% or you are charging; otherwise it waits until you plug in (§9.2). A
   notification shows progress. It is safe to kill the app — the batch resumes
   from where it stopped and never redoes finished work.

5. **Review before anything irreversible.** Graded results land in Review.
   Press and hold the image to see the original, release for the graded version.
   Pinch to 1:1; the comparison holds at zoom, which is the only way to judge
   sharpening. Under each frame is what the pipeline actually did: profile,
   iterations to converge, final skin b\*, which gates passed.

6. **Originals are only trashed when you approve.** That is a separate,
   second confirmation with different wording, never mixed into the triage batch
   (§8, trap #16). An original whose grade fell back is never eligible —
   trashing it would leave a re-encode as your only master (§9.3).

**Frames that failed a quality gate do not reach Review.** If the pipeline could
not improve a photo, it ships your original untouched and there is no decision
left to make, so it is closed out rather than queued. Earlier builds parked
these in Review and you ended up rejecting photos that were never changed. The
count is in **Settings**, with the gate that failed.

---

## Rejecting is how you tune it

Reject a graded photo and it is gone from the queue immediately — no dialog, no
interrogation. The confirmation snackbar carries a **Why?** action if you want
to say what was wrong; ignoring it is the normal case and costs nothing. That
optional tap is the only signal Sift gets (§9.5). After 50 rejections,
**Settings** shows the
distribution — a run of `TOO_WARM` means the b\* target of 17.0 is high for your
lighting, and you change one number instead of guessing.

Three inline fixes live next to the reject button:

- **Other profile** — regrades portrait as scene or vice versa, for when the
  router got it wrong.
- **Half strength** — the same decisions, applied at half the amount.
- **Keep original** — marks the frame `DO_NOT_GRADE`. Some photos are right as shot.

---

## Building it yourself

```sh
git clone https://github.com/brettadams0/sift
cd sift
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleRelease
```

The imaging pipeline needs no Android SDK at all:

```sh
./gradlew :core:imaging:test
```

### Signing your own builds

Without a keystore the release build falls back to the debug key, which is fine
for trying it but means Android will refuse to install an update signed with a
different key later. To own your signing key:

```sh
keytool -genkeypair -v -keystore sift-release.jks -alias sift \
  -keyalg RSA -keysize 4096 -validity 10000
```

Then create `keystore.properties` in the repo root:

```properties
storeFile=sift-release.jks
storePassword=...
keyAlias=sift
keyPassword=...
```

Both are gitignored and must stay that way. A signing key in a public repo lets
anyone build a package Android will happily install *over* yours, because it
treats matching package name plus matching signature as a legitimate update.

**If you change signing keys, uninstall first.** Android has no way to accept an
update signed by a different key, and the error it gives
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) does not explain itself.
