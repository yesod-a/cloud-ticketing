import { createSession } from './session'
type Envelope<T>={data:T}
const unpack=async<T>(response:Response):Promise<T>=>{if(!response.ok)throw new Error(`REQUEST_${response.status}`);return (await response.json() as Envelope<T>).data}
export const session=createSession(async()=>{try{return (await unpack<{accessToken:string}>(await fetch('/api/auth/refresh',{method:'POST',credentials:'include'}))).accessToken}catch{return false}})
export async function api<T>(url:string, init:RequestInit={}, retried=false):Promise<T>{const headers=new Headers(init.headers);if(session.token())headers.set('Authorization',`Bearer ${session.token()}`);const result=await fetch(url,{...init,headers,credentials:'include'});if(result.status===401&&!retried&&await session.refresh())return api<T>(url,init,true);if(result.status===401)session.clear();return unpack<T>(result)}
export const login=(identifier:string,password:string)=>api<{accessToken:string}>('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({identifier,password})}).then(v=>{session.set(v.accessToken);return v})
