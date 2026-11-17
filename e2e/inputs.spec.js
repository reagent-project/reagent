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
// overwritten.
// https://github.com/reagent-project/reagent/pull/630
//
// Async on-change (state updated on a later tick, like re-frame dispatch)
// leaves the rendered value behind the DOM. Reagent ignores renders of values
// that on-change already reported, and doesn't restore the rendered value
// when on-change keeps the state unchanged (#676).
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

  // Chinese pinyin IME: the composition shows the latin letters, then the
  // chosen characters are committed.
  const pinyin = [
    { compose: ['n', 'ni', 'ni h', 'ni ha', 'ni hao'], commit: '你好' },
    { compose: ['s', 'sh', 'shi', 'shi j', 'shi ji', 'shi jie'], commit: '世界' },
  ];
  const chineseWord = pinyin.map((s) => s.commit).join('');

  async function compose(page, id, steps) {
    const cdp = await page.context().newCDPSession(page);
    await page.getByTestId(id).focus();
    for (const { compose, commit } of steps) {
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

  // Before #676 was fixed, renders with lagging state wrote node.value during
  // composition, e.g. "ㄹ리리에에이이ㅈ전전ㅌ트" and
  // "ni hao你好shshishi jishi jie世界".
  for (const id of ['ime-sync', 'textarea-sync', 'ime-async']) {
    test(`Korean, ${id}`, async ({ page }) => {
      await compose(page, id, syllables);
      await expectValue(page, id, word);
    });
    test(`Chinese pinyin, ${id}`, async ({ page }) => {
      await compose(page, id, pinyin);
      await expectValue(page, id, chineseWord);
    });
  }
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

  // Before the fix, about every other key was lost
  // (e.g. "T uc bow xmsovrh lzd").
  test('async state', async ({ page }) => {
    await page.getByTestId('text-async').click();
    await page.keyboard.type(text, { delay: 10 });
    await expectValue(page, 'text-async', text);
  });
});

// #676 (comment): the input re-renders while the state lags behind the typed
// text, e.g. its parent re-renders when a background request finishes. The
// render carries the previously rendered value, which on-change reported
// before, so Reagent must not write it back.
// https://github.com/reagent-project/reagent/issues/676#issuecomment-6928985191
test.describe('re-render with lagging state (#676)', () => {
  const text = 'PPARG, HFE, GSTP1, CYP2D6';
  for (const id of ['text-async-rerender', 'textarea-async-rerender']) {
    test(id, async ({ page }) => {
      await page.getByTestId(id).click();
      await page.keyboard.type(text, { delay: 10 });
      await expectValue(page, id, text);
    });
  }
});

// #676 (comment): the state lags by more than a setTimeout and a frame (e.g.
// a busy re-frame queue). A check for a rejected change, which restored the
// rendered value after a render (2.0.x; later also after a setTimeout), wrote
// the lagging value back.
// https://github.com/reagent-project/reagent/issues/676#issuecomment-6951981693
test.describe('slow async state (#676)', () => {
  const text = 'PPARG, HFE, GSTP1, CYP2D6';
  for (const id of ['text-async-slow', 'textarea-async-slow']) {
    test(id, async ({ page }) => {
      await page.getByTestId(id).click();
      await page.keyboard.type(text, { delay: 10 });
      await expectValue(page, id, text);
    });
  }
});

// on-change rejects digits and puts the current value back into the input.
// (Keeping the state unchanged is a known issue, see below.)
test.describe('rejected change', () => {
  test('on-change restores the value, reject-restore-sync', async ({ page }) => {
    await page.getByTestId('reject-restore-sync').click();
    await page.keyboard.type('ab1c2d', { delay: 10 });
    await expectValue(page, 'reject-restore-sync', 'abcd');
  });

  test('caret mid-text after a restored value, reject-restore-sync', async ({ page }) => {
    const input = page.getByTestId('reject-restore-sync');
    await input.click();
    await page.keyboard.type('abcdef');
    for (let i = 0; i < 3; i++) await page.keyboard.press('ArrowLeft');
    await page.keyboard.type('x1y', { delay: 50 });
    await expectValue(page, 'reject-restore-sync', 'abcxydef');
    expect(await input.evaluate((el) => [el.selectionStart, el.selectionEnd])).toEqual([5, 5]);
  });
});

// Async on-change that transforms the value: the caret is still restored.
test('async upper-case, caret mid-text', async ({ page }) => {
  const input = page.getByTestId('upper-async');
  await input.click();
  await page.keyboard.type('abcdef', { delay: 50 });
  for (let i = 0; i < 3; i++) await page.keyboard.press('ArrowLeft');
  await page.keyboard.type('xy', { delay: 50 });
  await expectValue(page, 'upper-async', 'ABCXYDEF');
  expect(await input.evaluate((el) => [el.selectionStart, el.selectionEnd])).toEqual([5, 5]);
});

// Known issues, listed in doc/ControlledInputs.md. The fast typing ones are
// races, so a run can happen to produce the right value, which test.fail()
// reports as a failure; retries cover that.
test.describe('known issues', () => {
  test.describe.configure({ retries: 2 });

  // email and number inputs have no Selection API: when on-change changes the
  // value, Reagent writes it and the caret moves to the end, so the next key
  // goes to the end.
  const transformCases = [
    // on-change lower-cases: the typed "Q" is written back as "q".
    { id: 'email-lower-sync', initial: 'user@example.com', left: 4, insert: 'QR', expected: 'user@exampleqr.com' },
  ];
  for (const c of transformCases) {
    test(`caret mid-text when on-change changes the value, ${c.id}`, async ({ page }) => {
      test.fail(true, 'email/number inputs have no Selection API (#609)');
      await page.getByTestId(c.id).click();
      await page.keyboard.type(c.initial, { delay: 50 });
      for (let i = 0; i < c.left; i++) await page.keyboard.press('ArrowLeft');
      await page.keyboard.type(c.insert, { delay: 50 });
      await expectValue(page, c.id, c.expected, { timeout: 1000 });
    });
  }

  // on-change rejects digits by keeping the state unchanged: the typed text
  // stays in the input, as Reagent can't tell it apart from state that is
  // updated later (#676).
  for (const id of ['reject-sync', 'reject-async']) {
    test(`rejected key stays in the input, ${id}`, async ({ page }) => {
      test.fail(true, 'a change that keeps the state unchanged is not reverted');
      await page.getByTestId(id).click();
      await page.keyboard.type('ab1c2d', { delay: 50 });
      await page.waitForTimeout(400);
      // Every value after the digit has a digit too.
      await expectValue(page, id, 'ab', { timeout: 1000 });
    });
  }

  // on-change drops zeros: the value without the typed 0 is the current
  // state, so nothing is rendered and the 0 stays, like a rejected change.
  test('typed 0 stays, number-nozero-sync', async ({ page }) => {
    test.fail(true, 'a transform back to the current state is not written');
    await page.getByTestId('number-nozero-sync').click();
    await page.keyboard.type('12345', { delay: 50 });
    for (let i = 0; i < 2; i++) await page.keyboard.press('ArrowLeft');
    await page.keyboard.type('0', { delay: 50 });
    await expectValue(page, 'number-nozero-sync', '12345', { timeout: 1000 });
  });

  // Async on-change that transforms the value: a lagging transformed value
  // can't be told apart from a new value set by the app, so it is written.
  test('fast typing with async upper-case', async ({ page }) => {
    test.fail(true, 'transformed values from async on-change are written');
    const text = 'The quick brown fox jumps over the lazy d';
    await page.getByTestId('upper-async').click();
    await page.keyboard.type(text, { delay: 10 });
    await expectValue(page, 'upper-async', text.toUpperCase(), { timeout: 1000 });
  });
});

// Inputs rendered by a JS component (e.g. a component library): Reagent only
// sees the component, so its controlled input workaround isn't applied, and
// React's own controlled input moves the caret to the end after each key even
// with synchronous state. The same input also loses keys when typing fast and
// doubles IME letters ("ㄹ리링리에엥에이잊이저전전ㅌ트트" for 리에이전트,
// exactly the #587 report). Passing Reagent's :input to the component (like
// Material UI's inputComponent option) applies the workaround again.
test.describe('input rendered by a JS component', () => {
  async function typeMidText(page, id) {
    await page.getByTestId(id).click();
    await page.keyboard.type('abcdef', { delay: 100 });
    for (let i = 0; i < 3; i++) await page.keyboard.press('ArrowLeft');
    await page.keyboard.type('xy', { delay: 100 });
  }

  test('js-reagent-text-sync', async ({ page }) => {
    await typeMidText(page, 'js-reagent-text-sync');
    await expectValue(page, 'js-reagent-text-sync', 'abcxydef');
  });

  // Known issue: "abcxdefy".
  test('js-text-sync', async ({ page }) => {
    test.fail(true, 'no controlled input workaround for inputs inside JS components');
    await typeMidText(page, 'js-text-sync');
    await expectValue(page, 'js-text-sync', 'abcxydef', { timeout: 1000 });
  });
});
