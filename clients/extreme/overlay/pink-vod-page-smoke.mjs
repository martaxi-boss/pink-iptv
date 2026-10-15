// Render real built Movies and Series routes in Chromium, not only isolated
// workers/mappers. Regression for the owner's TESTE3 screenshots showing
// literal SSR "No playlist selected" when both route scripts never ran.
// Synthetic credentials only; no external provider or actual VPN.
import assert from "node:assert/strict"
import {createServer} from "node:http"
import {readFile, stat} from "node:fs/promises"
import {resolve, extname, join, sep} from "node:path"
import {chromium} from "playwright"

const root = resolve("dist")
const types = {".html":"text/html; charset=utf-8",".js":"text/javascript; charset=utf-8",".css":"text/css; charset=utf-8",".json":"application/json; charset=utf-8",".svg":"image/svg+xml",".png":"image/png",".webp":"image/webp",".woff2":"font/woff2"}
const server = createServer(async(req,res) => {
  try {
    const path = decodeURIComponent(new URL(req.url, "http://127.0.0.1").pathname)
    let file = resolve(root, "." + path)
    if (file !== root && !file.startsWith(root + sep)) {res.writeHead(403).end();return}
    let info = await stat(file).catch(()=>null)
    if (info?.isDirectory()) {file=join(file,"index.html");info=await stat(file).catch(()=>null)}
    if (!info && !extname(path)) {
      file=resolve(root,"."+path+".html")
      info=await stat(file).catch(()=>null)
    }
    if (!info?.isFile()) {res.writeHead(404).end("missing fixture asset");return}
    res.writeHead(200,{"content-type":types[extname(file)]||"application/octet-stream","cache-control":"no-store"})
    res.end(await readFile(file))
  } catch(error) {res.writeHead(500).end("test asset failure")}
})
let browser
try {
  await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve))
  browser = await chromium.launch({headless:true})
  const base = "http://127.0.0.1:"+server.address().port
  for (const route of ["movies","series"]) {
    for (const mode of ["no-account","android-account"]) {
      const context=await browser.newContext({viewport:{width:412,height:915},isMobile:true,hasTouch:true})
      const page=await context.newPage()
      const errors=[], requests=[]
      page.on("pageerror",e=>errors.push(e.message))
      page.on("console",msg=>{if(msg.type()==="error")errors.push(msg.text())})
      if(mode==="android-account"){
        await page.addInitScript(() => {
          const account={_id:"pink-smoke",title:"Synthetic QA",type:"xtream",serverUrl:"https://fixture.invalid",username:"synthetic",password:"synthetic"}
          localStorage.setItem("xt_playlists",JSON.stringify({entries:[account],selectedId:account._id}))
          const bodies={
            get_vod_categories:JSON.stringify([{category_id:"7",category_name:"Demo"}]),
            get_series_categories:JSON.stringify([{category_id:"7",category_name:"Demo"}]),
            get_vod_streams:JSON.stringify([{stream_id:71,name:"Fixture Movie",category_id:"7",category_ids:[7],stream_icon:""}]),
            get_series:JSON.stringify([{series_id:72,name:"Fixture Series",category_id:"7",category_ids:[7],cover:""}]),
          }
          const staging=new Map()
          window.PinkNative={onmessage:null,postMessage(text) {
            const request=JSON.parse(text)
            let result=true,ok=true,code
            if(request.operation==="vodCatalog"){
              const action=request.payload.action
              const body=bodies[action]
              if(!body){ok=false;code="UNSUPPORTED"}
              else {const bytes=new TextEncoder().encode(body);staging.set(action,{bytes,offset:0});result={token:action,size:bytes.length}}
            } else if(request.operation==="vodCatalogChunk"){
              const stage=staging.get(request.payload.token)
              if(!stage){ok=false;code="STAGE"}
              else if(stage.offset>=stage.bytes.length)result={done:true}
              else {const chunk=stage.bytes.slice(stage.offset,stage.offset+16384);stage.offset+=chunk.length;result={data:btoa(String.fromCharCode(...chunk))}}
            } else if(request.operation==="vodCatalogClose"){
              staging.delete(request.payload.token)
            } else if(request.operation==="vaultReadValidated"){
              result=JSON.stringify({entries:[account],selectedId:account._id})
            } else {ok=false;code="UNSUPPORTED"}
            queueMicrotask(()=>window.PinkNative.onmessage?.({data:JSON.stringify({id:request.id,ok,result,code})}))
          }}
          window.__pinkSmokeNativeStats={staging}
        })
      }
      const statusId=route==="movies"?"movie-list-status":"series-list-status"
      const url=base+"/"+route+"/"
      const response=await page.goto(url,{waitUntil:"domcontentloaded"})
      assert.equal(response?.status(),200,route+" missing built page")
      try {
        await page.waitForFunction(id=>{
          const element=document.getElementById(id)
          return element && !/^no playlist selected\.?$/i.test(element.textContent.trim())
        },statusId,{timeout:15000})
        if(mode==="android-account"){
          await page.waitForFunction(id=>{
            const text=document.getElementById(id)?.textContent||""
            return /nesta categoria|in catalogue|total movies|total series/i.test(text)
          },statusId,{timeout:20000})
          const cards=await page.locator("#"+(route==="movies"?"movie-grid":"series-grid")+" > :not([data-skeleton])").count()
          assert.ok(cards>0,route+" did not render the native fixture catalog")
        } else {
          const link=await page.locator("#"+statusId+" a[href='/login']").count()
          assert.ok(link>0,route+" did not show actionable missing-account state")
        }
      } catch(error) {
        const state=await page.evaluate(id=>({text:document.getElementById(id)?.textContent,grid:document.getElementById(id.replace("list-status","grid"))?.children?.length}),statusId)
        throw Error("PINK_VOD_BOOTSTRAP_FAILED "+JSON.stringify({route,mode,state,errors:errors.slice(0,6),requestLog:requests.slice(0,6)}),{cause:error})
      }
      const fatal=errors.filter(error=>/uncaught|referenceerror|syntaxerror|cannot access|not defined|failed to fetch dynamically imported/i.test(error))
      assert.deepEqual(fatal,[],route+" client bootstrap JavaScript error")
      console.log("PINK_VOD_BROWSER_BOOTSTRAP=PASS",route,mode)
      await context.close()
    }
  }
} finally {
  await browser?.close()
  await new Promise(resolve=>server.close(resolve))
}
