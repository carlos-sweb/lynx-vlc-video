// "mithril-runtime" (https://github.com/carlos-sweb/mithril-runtime) has no
// types of its own yet — it's a same-shaped subset of real Mithril (m.route/
// m.trust/m.request removed). Reusing @types/mithril here is a deliberate
// approximation: it still types m.route/m.trust/m.request as present, which
// mithril-runtime doesn't have — this package never references those.
declare module "mithril-runtime" {
	import m from "mithril";
	export = m;
}
