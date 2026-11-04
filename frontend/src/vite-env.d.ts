/// <reference types="vite/client" />

declare module '*.css' {
  const stylesheet: string
  export default stylesheet
}

interface ImportMetaEnv {
  readonly VITE_DEMO_API_TOKEN?: string
}
interface ImportMeta {
  readonly env: ImportMetaEnv
}
