# `object-fit`

How the decoded picture sits in the element’s box (the black rectangle). Default is `contain`.

| Value | Picture | Box |
|---|---|---|
| `contain` | Whole frame, aspect preserved | Touches at least one pair of edges. Letterbox on the other axis if the aspect ratios differ. |
| `cover` | Aspect preserved | Fills the box; may crop. |
| `fill` | May stretch | Touches all four edges. |

```js
vlcVideo({
  id: "player",
  class: "Player",
  src: url,
  "object-fit": "contain", // or "cover" | "fill"
});
```

On a 16:9 live stream in a `100% × 220px` box (portrait phone), `contain` fills the **width** and adds thin bars top and bottom. That is correct `contain`. A small 16:9 rectangle floating with black on all four sides is **not** — that was a libVLC portrait viewport swap, fixed with `setUseOrientationFromBounds(true)`.

You can change `object-fit` while playing; the native layer reapplies scale on layout and on the first video output.

## Pitfalls

- Size the box with CSS. `object-fit` does not invent a height.
- `cover` and `fill` look similar on a box that is already close to 16:9; use a shorter box to tell them apart.
