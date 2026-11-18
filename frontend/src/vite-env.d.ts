/// <reference types="vite/client" />
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  /** "true" shows the demo-account quick-login list (never enable on a public production site). */
  readonly VITE_SHOW_DEMO_ACCOUNTS?: string;
}
interface ImportMeta {
  readonly env: ImportMetaEnv;
}
