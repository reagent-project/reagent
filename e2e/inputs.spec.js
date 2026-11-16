// Real, trusted input against Reagent's controlled input workaround
// (src/reagent/impl/input.cljs). The page is test/reagenttest/e2e/inputs.cljs:
// each scenario is an <input data-testid=ID> plus <pre data-testid=ID-state>
// showing the value in the ratom.
//
// The sync-state cases (#609, #595, sync IME and fast typing) all failed
// before #630 (5baa4cf), which runs Reagent's render queue inside
// react-dom/flushSync. Before that, with a createRoot root, forceUpdate from
// the requestAnimationFrame render queue was only scheduled by React, so the
// after-render callback queued by input-handle-change ran before the
// re-render, saw a stale rendered value and wrote it into node.value; the
// later commit then wrote the new value again. For email/number (no Selection
// API) each write moves the caret to the end, and keys typed in between were
// overwritten. Async on-change (#676) still leaves the rendered value behind
// the DOM, so the same kind of overwrite remains there.
// https://github.com/reagent-project/reagent/pull/630
const { test, expect } = require('@playwright/test');

test.beforeEach(async ({ page }) => {
  await page.goto('/');
  await expect(page.getByTestId('text-sync')).toBeVisible();
});

// Exact comparison: toHaveText would normalize whitespace.
const stateText = (page, id) =>
  page.getByTestId(`${id}-state`).evaluate((el) => el.textContent);

async function expectValue(page, id, value, options) {
  await expect(page.getByTestId(id)).toHaveValue(value, options);
  await expect.poll(() => stateText(page, id), options).toBe(value);
}

// Known bugs are marked with test.fail(). They are races, so one run can
// happen to produce the right value, which test.fail() reports as a failure;
// retries cover that. The short timeout keeps the expected failure fast.
const knownBugRetries = { retries: 2 };
const knownBugTimeout = { timeout: 500 };

// #609: typing in the middle of the text must not move the caret to the end.
// email and number inputs have no Selection API, so Reagent can't restore the
// caret there; it must avoid touching node.value instead.
// Fixed by #630 for sync state (see top of file).
// https://github.com/reagent-project/reagent/issues/609
test.describe('caret position when typing mid-text (#609)', () => {
  const cases = [
    { id: 'text-sync', initial: 'abcdef', left: 3, insert: 'XY', expected: 'abcXYdef', caret: 5 },
    { id: 'textarea-sync', initial: 'abcdef', left: 3, insert: 'XY', expected: 'abcXYdef', caret: 5 },
    { id: 'textarea-upper-sync', initial: 'abcdef', left: 3, insert: 'xy', expected: 'ABCXYDEF', caret: 5 },
    { id: 'email-sync', initial: 'user@example.com', left: 4, insert: '-x', expected: 'user@example-x.com' },
    { id: 'number-sync', initial: '12345', left: 2, insert: '9', expected: '123945' },
    // on-change upper-cases, so Reagent writes node.value and must restore the caret.
    { id: 'upper-sync', initial: 'abcdef', left: 3, insert: 'xy', expected: 'ABCXYDEF', caret: 5 },
  ];
  for (const c of cases) {
    test(c.id, async ({ page }) => {
      const input = page.getByTestId(c.id);
      await input.click();
      await page.keyboard.type(c.initial);
      for (let i = 0; i < c.left; i++) await page.keyboard.press('ArrowLeft');
      await page.keyboard.type(c.insert);
      await expectValue(page, c.id, c.expected);
      if (c.caret !== undefined) {
        expect(await input.evaluate((el) => [el.selectionStart, el.selectionEnd]))
          .toEqual([c.caret, c.caret]);
      }
    });
  }
});

// #587: IME composition (Korean) must not double jamo or lose the
// composed text. Uses CDP Input.imeSetComposition + Input.insertText, with a
// short pause after each step (without pauses the async case is racy and
// sometimes passes).
// https://github.com/reagent-project/reagent/issues/587
test.describe('IME composition (#587)', () => {
  // "리에이전트", the example from the issue, as a 2-set Korean IME sends it:
  // the next consonant first joins the current syllable as a final (리 -> 링)
  // and moves to the next syllable when a vowel follows (링 -> 리 + 에), at
  // which point the previous syllable is committed.
  const syllables = [
    { compose: ['ㄹ', '리', '링'], commit: '리' },
    { compose: ['에', '엥'], commit: '에' },
    { compose: ['이', '잊'], commit: '이' },
    { compose: ['저', '전'], commit: '전' },
    { compose: ['ㅌ', '트'], commit: '트' },
  ];
  const word = syllables.map((s) => s.commit).join('');

  async function composeKorean(page, id) {
    const cdp = await page.context().newCDPSession(page);
    await page.getByTestId(id).focus();
    for (const { compose, commit } of syllables) {
      for (const text of compose) {
        await cdp.send('Input.imeSetComposition', {
          text, selectionStart: text.length, selectionEnd: text.length,
        });
        await page.waitForTimeout(20);
      }
      await cdp.send('Input.insertText', { text: commit });
      await page.waitForTimeout(20);
    }
  }

  for (const id of ['ime-sync', 'textarea-sync']) {
    test(`sync state, ${id}`, async ({ page }) => {
      await composeKorean(page, id);
      await expectValue(page, id, word);
    });
  }

  // Known failure: with on-change updating state on a later tick, the
  // in-progress composition text is kept, e.g. "ㄹ리리에에이이ㅈ전전ㅌ트",
  // matching the report.
  // Likely the same cause as #676 (a render with lagging state writes
  // node.value).
  test.describe('async state', () => {
    test.describe.configure(knownBugRetries);
    test('ime-async', async ({ page }) => {
      test.fail(true, 'https://github.com/reagent-project/reagent/issues/587');
      await composeKorean(page, 'ime-async');
      await expectValue(page, 'ime-async', word, knownBugTimeout);
    });
  });
});

// #595: when the text overflows a narrow input, the caret (end of text) must
// stay scrolled into view. Fails before #630 (see top of file).
// Checked after every key: native typing keeps scrollLeft within 1px of the
// maximum. Letters only, since keys that on-change doesn't change (e.g.
// digits when upper-casing) let the browser scroll back into place.
// https://github.com/reagent-project/reagent/issues/595
test.describe('caret visible at the end of a full input (#595)', () => {
  async function scrollGaps(page, id, transform) {
    const input = page.getByTestId(id);
    await input.click();
    const text = 'abcdefghijklmnopqrstuvwxyz';
    const gaps = [];
    for (let i = 0; i < text.length; i++) {
      await page.keyboard.press(text[i]);
      await expect(input).toHaveValue(transform(text.slice(0, i + 1)));
      const m = await input.evaluate((el) => new Promise((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(() => resolve({
          gap: el.scrollWidth - el.clientWidth - el.scrollLeft,
          overflow: el.scrollWidth > el.clientWidth,
          caret: el.selectionStart,
        }))),
      ));
      expect(m.caret).toBe(i + 1);
      if (m.overflow) gaps.push(m.gap);
    }
    expect(gaps.length).toBeGreaterThan(5);
    return gaps;
  }

  test('narrow-sync', async ({ page }) => {
    const gaps = await scrollGaps(page, 'narrow-sync', (s) => s);
    expect(gaps.filter((g) => g > 1)).toEqual([]);
  });

  // Known failure: Reagent writes the upper-cased (wider) value and sets the
  // selection, and Chrome then scrolls only until the caret position itself is
  // inside the input, so the last character and the caret are cut off by
  // 2-10px.
  test('narrow-upper-sync', async ({ page }) => {
    test.fail(true, 'https://github.com/reagent-project/reagent/issues/595');
    const gaps = await scrollGaps(page, 'narrow-upper-sync', (s) => s.toUpperCase());
    expect(gaps.filter((g) => g > 1)).toEqual([]);
  });
});

// #676: on-change that updates state on a later tick (like re-frame dispatch).
// A render with lagging state overwrites newer typed text, so keys get lost.
// https://github.com/reagent-project/reagent/issues/676
test.describe('fast typing (#676)', () => {
  const text = 'The quick brown fox jumps over the lazy d';

  test('sync state', async ({ page }) => {
    await page.getByTestId('text-sync').click();
    await page.keyboard.type(text, { delay: 10 });
    await page.waitForTimeout(400);
    await expectValue(page, 'text-sync', text);
  });

  // Known failure: roughly every other key is lost
  // (e.g. "T uc bow xmsovrh lzd").
  test.describe('async state', () => {
    test.describe.configure(knownBugRetries);
    test('text-async', async ({ page }) => {
      test.fail(true, 'https://github.com/reagent-project/reagent/issues/676');
      await page.getByTestId('text-async').click();
      await page.keyboard.type(text, { delay: 10 });
      await expectValue(page, 'text-async', text, knownBugTimeout);
    });
  });
});
