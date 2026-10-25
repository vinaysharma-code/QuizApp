async function prepare(base){
 if(process.env.BROWSER_TIMED_ID)return;
 const api=async(path,data,token)=>{const r=await fetch(base+path,{method:'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:JSON.stringify(data)});if(!r.ok){const error=new Error(`Fixture ${path}: HTTP ${r.status}`);error.status=r.status;throw error;}const text=await r.text();try{return JSON.parse(text);}catch{return text;}};
 const admin=await api('/auth/login',{email:process.env.DEMO_ADMIN_EMAIL,password:process.env.DEMO_ADMIN_PASSWORD});
 const suffix=require('node:crypto').randomUUID();const selected=[];const answers={};
 for(let i=0;i<2;i++){const q=await api('/question/addQuestions',{questionTitle:`Browser ${suffix} ${i}`,optionA:'JVM',optionB:'CSS',optionC:'HTML',optionD:'SQL',rightAnswer:'JVM',difficultyLevel:'easy',category:'browser-'+suffix},admin.token);}
 const bank=await fetch(base+'/question/getAllQuestion',{headers:{Authorization:'Bearer '+admin.token}});if(!bank.ok)throw new Error('Fixture bank failed');for(const q of await bank.json()){if(q.category==='browser-'+suffix){selected.push(q.id);answers[q.id]='JVM';}}assert.equal(selected.length,2);
 const create=async(title,durationSeconds)=>{const draft=await api('/admin/quizzes',{title,durationSeconds,questionIds:selected,maxAttempts:3,passingPercent:50,resultReleasePolicy:'IMMEDIATE'},admin.token);for(let retry=0;;retry++){try{await api('/admin/quizzes/'+draft.quiz.id+'/publish',{},admin.token);break;}catch(error){if(error.status!==503||retry>=29)throw error;await new Promise(resolve=>setTimeout(resolve,2000));}}return draft.quiz.id;};
 process.env.BROWSER_TITLE='Browser '+suffix;
 process.env.BROWSER_TIMED_TITLE='Timed '+suffix;
 process.env.BROWSER_QUIZ_ID=await create(process.env.BROWSER_TITLE,600);
 process.env.BROWSER_TIMED_ID=await create(process.env.BROWSER_TIMED_TITLE,30);
 process.env.BROWSER_ANSWERS=JSON.stringify(answers);
 if(!process.env.DEMO_EMAIL){process.env.DEMO_EMAIL='browser-'+suffix+'@example.test';process.env.DEMO_PASSWORD='StudentPassword123!';process.env.REGISTER_IN_BROWSER='1';}
}
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
(async()=>{
 const base=process.env.BASE_URL||'http://127.0.0.1:8765';await prepare(base);
 let launch={headless:true};
 if(process.env.BROWSER_EXECUTABLE)launch={...launch,executablePath:process.env.BROWSER_EXECUTABLE,args:['--no-sandbox']};
 const browser=await chromium.launch(launch);
 try{
 const context=await browser.newContext({viewport:{width:1280,height:900}});
 const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
 page.on('response',r=>{if(r.status()>=400)console.log('HTTP',r.status(),new URL(r.url()).pathname);});page.on('pageerror',e=>console.log('JS',e.message));
 const login=async(password=process.env.DEMO_PASSWORD)=>{await page.locator('#login input[name=email]').fill(process.env.DEMO_EMAIL);await page.locator('#login input[name=password]').fill(password);await page.locator('#login button').click();await page.locator('#session').waitFor({state:'visible'});};
 const waitSaved=async()=>{await page.waitForTimeout(600);return page.waitForFunction(()=>document.getElementById('saveStatus').textContent==='All answers saved.');};
 const creatorPage=await context.newPage();
 await creatorPage.goto(base+'/demo.html');
 assert.equal(await creatorPage.locator('#register select[name=accountType]').inputValue(),'CREATOR');
 await creatorPage.locator('#register input[name=userName]').fill('Public creator');
 await creatorPage.locator('#register input[name=email]').fill('creator-'+Date.now()+'@example.test');
 await creatorPage.locator('#register input[name=password]').fill('CreatorPassword123!');
 await creatorPage.locator('#register button').click();
 await creatorPage.locator('#admin').waitFor({state:'visible'});
 assert.equal(await creatorPage.locator('#bank').textContent(),'Add questions first.');
 await creatorPage.locator('#logout').click();await creatorPage.close();
 console.log('PASS public creator signup, automatic login and private empty bank');
 await page.goto(base+'/demo.html?quiz='+process.env.BROWSER_QUIZ_ID);
 await page.locator('#linkedQuiz').waitFor({state:'visible'});
 assert.equal(await page.locator('#linkedStart').isVisible(),false);
 if(process.env.REGISTER_IN_BROWSER){await page.locator('#register input[name=userName]').fill('Browser student');await page.locator('#register input[name=email]').fill(process.env.DEMO_EMAIL);await page.locator('#register input[name=password]').fill(process.env.DEMO_PASSWORD);await page.locator('#register button').click();await page.locator('#session').waitFor({state:'visible'});}
 if(!process.env.REGISTER_IN_BROWSER)await login();await page.locator('#linkedStart').waitFor({state:'visible'});
 assert.equal(await page.locator('#linkedTitle').textContent(),process.env.BROWSER_TITLE);
 assert.equal(await page.locator('#catalog').isVisible(),false);
 await page.locator('#linkedStart').click();
 await page.locator('#attempt').waitFor({state:'visible'});
 const attemptId=await page.evaluate(()=>current.id);
 for(const field of await page.locator('#questions fieldset').all())await field.getByLabel('JVM',{exact:true}).check();
 await waitSaved();await page.reload();await page.locator('#linkedStart').waitFor({state:'visible'});assert(page.url().includes('?quiz='));await page.locator('#linkedStart').click();await page.locator('#attempt').waitFor({state:'visible'});
 assert.equal(await page.evaluate(()=>current.id),attemptId);assert.equal(await page.locator('#questions input:checked').count(),2);
 // A rejected access token must renew through the cookie while preserving selections.
 await page.evaluate(()=>{token='expired-test-token';});await page.locator('#questions fieldset').first().getByLabel('CSS',{exact:true}).check();await waitSaved();assert.notEqual(await page.evaluate(()=>token),'expired-test-token');
 console.log('PASS browser login, autosave, reload/resume and transparent access renewal');
 // Use a second tab with the same rotating cookie and an independent answer revision.
 const tab=await context.newPage();tab.on('pageerror',e=>errors.push(e.message));tab.on('dialog',d=>d.accept());await tab.goto(base+'/demo.html');await tab.locator('#session').waitFor({state:'visible'});
 await tab.evaluate(id=>openAttempt(id),attemptId);
 await page.locator('#questions fieldset').first().getByLabel('JVM',{exact:true}).check();await waitSaved();
 await tab.locator('#questions fieldset').first().getByLabel('SQL',{exact:true}).check();await tab.locator('#reloadConflict').waitFor({state:'visible'});
 await tab.locator('#reloadConflict').click();await tab.waitForFunction(()=>document.getElementById('reloadConflict').hidden);
 assert.equal(await tab.locator('#questions fieldset').first().locator('input:checked').inputValue(),'JVM');await tab.close();
 await page.locator('#submit').click();await page.waitForFunction(()=>document.getElementById('result').textContent.includes('Recorded score: 2 / 2'));
 console.log('PASS browser cross-tab conflict protection and submission');
 await page.goto(base+'/demo.html');await page.locator('#session').waitFor({state:'visible'});await page.locator('#catalogSearch input').fill(process.env.BROWSER_TIMED_TITLE||'Browser timed assessment');await page.locator('#catalogSearch button').click();await page.locator('#quizzes .card').filter({has:page.getByRole('heading',{name:process.env.BROWSER_TIMED_TITLE||'Browser timed assessment',exact:true})}).getByRole('button').click();
 await page.waitForFunction(id=>current?.quizId===id,process.env.BROWSER_TIMED_ID);
 const answers=JSON.parse(process.env.BROWSER_ANSWERS);
 for(const field of await page.locator('#questions fieldset').all()){const id=await field.locator('input').first().getAttribute('name');await field.getByLabel(answers[id],{exact:true}).check();}
 await waitSaved();await page.waitForFunction(()=>document.getElementById('result').textContent.includes('Recorded score: 2 / 2'),null,{timeout:45000});
 assert.equal(await page.locator('#submit').isVisible(),false);
 console.log('PASS visible countdown and server finalization of autosaved answers');
 await page.setViewportSize({width:390,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1));
 
 await page.locator('#logout').click();await page.locator('#auth').waitFor({state:'visible'});await page.reload();await page.locator('#auth').waitFor({state:'visible'});
 console.log('PASS mobile width and logout session revocation');
 const adminPage=await context.newPage();await adminPage.goto(base+'/demo.html');await adminPage.locator('#login input[name=email]').fill(process.env.DEMO_ADMIN_EMAIL);await adminPage.locator('#login input[name=password]').fill(process.env.DEMO_ADMIN_PASSWORD);await adminPage.locator('#login button').click();await adminPage.locator('#owned').waitFor({state:'visible'});
 const sharedCard=adminPage.locator('#mine .card').filter({has:adminPage.getByRole('heading',{name:process.env.BROWSER_TITLE,exact:true})});await sharedCard.getByRole('button',{name:'Copy quiz link'}).waitFor();assert.equal(await sharedCard.getByLabel('Share quiz link').inputValue(),base+'/demo.html?quiz='+process.env.BROWSER_QUIZ_ID);assert.equal(await adminPage.locator('#catalog').isVisible(),false);await sharedCard.getByRole('button',{name:'Copy quiz link'}).click();await adminPage.close();
 console.log('PASS shared quiz registration/login, persistent link and admin share control');
 if(process.env.BROWSER_VERIFY_TOKEN&&process.env.BROWSER_RESET_TOKEN){await page.goto(base+'/demo.html#verify='+process.env.BROWSER_VERIFY_TOKEN);await page.waitForFunction(()=>document.getElementById('notice').textContent.includes('Email verified'));
 await page.goto(base+'/demo.html#reset='+process.env.BROWSER_RESET_TOKEN);await page.locator('#resetPassword input').fill('ChangedPassword123!');await page.locator('#resetPassword button').click();await page.waitForFunction(()=>document.getElementById('notice').textContent.includes('Password changed'));
 await login('ChangedPassword123!');assert.deepEqual(errors,[]);
 console.log('PASS browser verification/reset forms');}
 assert.deepEqual(errors,[]);console.log('PASS zero JavaScript page errors');
 }finally{await browser.close();}
})().catch(e=>{console.error(e.stack);process.exit(1)});
