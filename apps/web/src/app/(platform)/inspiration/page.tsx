"use client";
/* eslint-disable @next/next/no-img-element -- media host is runtime configured */
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import type { Showcase } from "@wedjan/shared";
import { api } from "@/lib/api";
import { DiscoveryHeader } from "../search/search-experience";
import styles from "../discovery.module.css";

export default function InspirationPage(){const [items,setItems]=useState<Showcase[]>([]);const [cursor,setCursor]=useState<string>();const [loading,setLoading]=useState(true);const lastTap=useRef<Record<string,number>>({});
  async function load(next?:string){setLoading(true);const {data}=await api.GET("/api/v1/showcases",{params:{query:next?{cursor:next}:{}}});if(data){setItems(old=>next?[...old,...data.items]:data.items);setCursor(data.nextCursor);}setLoading(false)}
  useEffect(()=>{let active=true;api.GET("/api/v1/showcases",{params:{query:{}}}).then(({data})=>{if(active&&data){setItems(data.items);setCursor(data.nextCursor);setLoading(false)}});return()=>{active=false}},[]);
  async function favorite(showcase:Showcase){await api.POST("/api/v1/discovery/favorites",{body:{entityType:"SHOWCASE",entityId:showcase.id}});setItems(old=>old.map(x=>x.id===showcase.id?{...x,favorite:true}:x));}
  return <div className={styles.page}><DiscoveryHeader/><main className={styles.shell}><section className={styles.hero}><h1>Real events, credited to the people who made them.</h1><p>Every visible vendor credit has been confirmed by that business. Double-tap a story to save it.</p></section><div className={styles.masonry}>{items.map(item=><article className={styles.story} key={item.id} onClick={()=>{const now=Date.now();if(now-(lastTap.current[item.id]??0)<320)void favorite(item);lastTap.current[item.id]=now}}>{item.coverUrl&&<Link href={`/inspiration/${item.slug}`}><img src={item.coverUrl} alt={item.title}/></Link>}<div className={styles.storyBody}><button className={styles.favorite} aria-label="Favorite" onClick={e=>{e.stopPropagation();void favorite(item)}}>{item.favorite?"♥":"♡"}</button><h2><Link href={`/inspiration/${item.slug}`}>{item.title}</Link></h2><p>{item.eventType.toLowerCase()} · {item.city} · by <Link href={`/v/${item.ownerVendorSlug}`}>{item.ownerBusinessName}</Link></p><div className={styles.chips}>{item.styleTags.map(tag=><span className={styles.chip} key={tag}>{tag}</span>)}</div></div></article>)}</div>{cursor&&<button className={`${styles.button} ${styles.loadMore}`} disabled={loading} onClick={()=>void load(cursor)}>{loading?"Loading…":"Load more inspiration"}</button>}{!loading&&items.length===0&&<div className={styles.empty}>The first real events are being prepared.</div>}</main></div>}
