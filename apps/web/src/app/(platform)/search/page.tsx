import { Suspense } from "react";
import { SearchExperience } from "./search-experience";

export default function SearchPage(){return <Suspense fallback={<div className="auth-shell">Finding vendors…</div>}><SearchExperience/></Suspense>;}
