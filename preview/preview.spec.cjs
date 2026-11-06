const {test,expect}=require('@playwright/test');
const path=require('path'),fs=require('fs');
const modules=require('../design/ui01b-tokens.json').modules;
const selector=id=>'[data-testid="'+id+'"]';
async function goto(page){await page.goto('http://127.0.0.1:4173');}
async function modulePage(page,name){await page.locator('[data-module="'+name+'"]').click();await expect(page.locator('#module-title')).toHaveText(name);}
async function add(page,name,title){await modulePage(page,name);await page.locator('#title').fill(title);if(name==='Finance')await page.locator('#amount').fill('12,34');else await page.locator('#body').fill('TEST PRIVATE <img src=x onerror=alert(1)>');if(['Administratif','Projets'].includes(name))await page.locator('#due').fill(new Date().toISOString().slice(0,10));await page.locator('#entry-form button[type=submit]').click();const row=page.locator('.record').filter({has:page.locator('h3',{hasText:title})}).first();await expect(row).toBeVisible();return await row.getAttribute('data-entry-id');}

for(const [label,width,height] of [['phone-portrait',412,915],['phone-landscape',915,412],['tablet-portrait',800,1280],['tablet-landscape',1280,800]]){
 test('navigation, settings, ambience, power and layout '+label,async({page})=>{
  test.setTimeout(120000);await page.setViewportSize({width,height});await goto(page);
  expect(await page.locator('nav span').evaluateAll(nodes=>nodes.every(n=>n.scrollWidth<=n.clientWidth&&n.getBoundingClientRect().left>=n.closest('button').getBoundingClientRect().left&&n.getBoundingClientRect().right<=n.closest('button').getBoundingClientRect().right))).toBeTruthy();
  for(const [id,name] of [['nav-modules','Modules'],['nav-documents','Documents'],['nav-calendar','Calendrier'],['nav-statistics','Statistiques'],['nav-settings','Paramètres'],['supervision','Supervision'],['avatar','Avatar']]){
   await page.locator('#logo-home').click();await page.locator('#'+id).click();await expect(page.locator('#module-title')).toHaveText(name);if(name==='Calendrier')expect(await page.locator('.calendar-grid button').evaluateAll(nodes=>nodes.every(n=>n.getBoundingClientRect().width>=47.9&&n.getBoundingClientRect().height>=47.9))).toBeTruthy();await page.locator('#back').click();await expect(page.locator('#workspace')).toBeHidden();
  }
  await page.locator('#nav-modules').click();
  for(const m of modules){await page.locator(selector('list-module-'+m.name)).click();await expect(page.locator('#module-title')).toHaveText(m.name);await page.locator('#back').click();await expect(page.locator('#module-title')).toHaveText('Modules');}
  await page.locator('#home').click();await page.locator('#nav-settings').click();await page.locator('#logo-home').click();await expect(page.locator('#workspace')).toBeHidden();
  for(const m of modules){await modulePage(page,m.name);expect(await page.evaluate(()=>document.documentElement.style.getPropertyValue('--accent'))).toBe(m.color);}
  await page.locator('#nav-settings').click();
  for(const p of ['economy','balanced','quality']){await page.locator('#performance').selectOption(p);await expect(page.locator('#performance')).toHaveValue(p);}
  await page.locator('#intensity').fill('0');await page.locator('#intensity').dispatchEvent('input');await expect(page.locator('body')).toHaveClass(/economy/);
  await page.locator('#intensity').fill('45');await page.locator('#intensity').dispatchEvent('input');await page.locator('#glow').uncheck();await expect(page.locator('body')).toHaveClass(/no-glow/);await page.locator('#glow').check();
  await page.locator('#auto-cycle').uncheck();await page.locator('#hour').fill('22');await page.locator('#hour').dispatchEvent('input');
  for(const weather of ['CLEAR','CLOUDS','RAIN','STORM','FOG','SNOW']){await page.locator('#weather').selectOption(weather);await expect(page.locator('#scene-label')).toContainText('DÉMONSTRATION');}
  await expect(page.locator('#ambience')).toContainText('NUIT');await expect(page.locator('#weather-art circle')).toHaveCount(33);
  await modulePage(page,'Finance');await expect(page.locator('#weather')).toHaveValue('SNOW');await expect(page.locator('#weather-art circle')).toHaveCount(33);
  await page.locator('#nav-settings').click();await page.locator('#ambience-auto').click();await expect(page.locator('#auto-cycle')).toBeChecked();await expect(page.locator('#weather-art')).toBeEmpty();
  await page.locator('#auto-cycle').uncheck();await page.locator('#auto-cycle').check();
  await page.locator('#layout-mode').selectOption('portrait');await expect(page.locator('body')).toHaveClass(/portrait/);await page.locator('#orientation').click();await expect(page.locator('body')).toHaveClass(/force-landscape/);await page.locator('#orientation').click();await expect(page.locator('body')).toHaveClass(/portrait/);await page.locator('#layout-mode').selectOption('auto');
  await modulePage(page,'Sécurité');await expect(page.locator('#native-note')).toContainText('indisponibles');await page.locator('#privacy').check();await expect(page.locator('body')).toHaveClass(/privacy-mask/);await page.locator('#privacy').uncheck();
  await modulePage(page,'Technique');await page.locator('#diagnostic-refresh').click();await expect(page.locator('#diagnostic')).toContainText('navigateur uniquement');const downloadPromise=page.waitForEvent('download');await page.locator('#diagnostic-export').click();const download=await downloadPromise;const report=fs.readFileSync(await download.path(),'utf8');expect(report).toContain('navigateur uniquement');expect(report).not.toContain('TEST PRIVATE');
  await page.locator('#nav-power').click();await expect(page.locator('#power-dialog')).toContainText('n’est pas disponible');await page.locator('#power-cancel').click();await expect(page.locator('#power-dialog')).not.toBeVisible();
  await page.locator('#nav-power').click();await page.locator('#power-sleep').click();await expect(page.locator('body')).toHaveClass(/sleeping/);await page.locator('#power-resume').click();await expect(page.locator('body')).not.toHaveClass(/sleeping/);await expect(page.locator('#module-title')).toHaveText('Technique');
  await page.locator('#home').click();await page.locator('#nav-modules').click();await modulePage(page,'Finance');await page.goBack();await expect(page.locator('#module-title')).toHaveText('Modules');await modulePage(page,'Finance');await page.keyboard.press('Alt+ArrowLeft');await expect(page.locator('#module-title')).toHaveText('Modules');
  await page.locator('#home').click();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();await page.screenshot({path:'test-results/ui01c-'+label+'.png',fullPage:true});
 });
}

test('CRUD all local modules, finance chart, steps, histories, collections and file boundaries',async({page})=>{
 test.setTimeout(180000);await goto(page);
 for(const name of ['Administratif','Finance','Projets','Écriture','Création']){
  const title='DEMO '+name;const id=await add(page,name,title);const row=()=>page.locator('[data-entry-id="'+id+'"]');
  await expect(row().locator('img')).toHaveCount(0);
  await page.locator(selector('edit-'+id)).click();await page.locator('#edit-title').fill('NON ENREGISTRÉ');await page.locator('#edit-cancel').click();await expect(row().locator('h3')).toHaveText(title);
  await page.locator(selector('edit-'+id)).click();await page.locator('#edit-title').fill(title+' modifié');await page.locator('#edit-body').fill('Texte modifié');
  if(name==='Finance'){await page.locator('#edit-amount').fill('invalid');await page.locator('#edit-save').click();await expect(page.locator('#edit-error')).toContainText('invalide');await page.locator('#edit-amount').fill('2,50');await page.locator('#edit-sign').selectOption('income');}
  await page.locator('#edit-save').click();await expect(row().locator('h3')).toHaveText(title+' modifié');await expect(row()).toContainText('Texte modifié');
  await page.locator(selector('toggle-'+id)).click();await expect(row()).toContainText('Terminé / exclu');await page.locator(selector('toggle-'+id)).click();await expect(row()).not.toContainText('Terminé / exclu');
  if(name==='Finance'){
   await page.locator('#budget').fill('bad');await page.locator('#save-budget').click();await expect(page.locator('#message')).toContainText('invalide');await page.locator('#budget').fill('100,25');await page.locator('#save-budget').click();await expect(page.locator('#totals')).toContainText('100,25');await expect(page.locator('#finance-chart')).toHaveAttribute('aria-label',/2,50/);
   await page.locator('#amount').fill('-2');await page.locator('#entry-form button[type=submit]').click();await expect(page.locator('#message')).toContainText('invalide');await page.locator('#movement').selectOption('income');await page.locator('#movement').selectOption('expense');
  }
  if(['Projets','Création'].includes(name)){
   const progress=page.locator(selector('project-progress-'+id));await progress.fill('75');await progress.dispatchEvent('input');await progress.dispatchEvent('change');
   await page.locator(selector('step-title-'+id)).fill('Étape démo');await page.locator(selector('step-add-'+id)).click();const check=row().locator('input[type=checkbox]');await check.check();await check.uncheck();await check.check();
   await page.locator(selector('history-'+id)).click();await expect(page.locator('#history-content')).toContainText('Progression : 75 %');await expect(page.locator('#history-content')).toContainText('Étape terminée');await page.locator('#history-close').click();
  }
  await page.reload();await expect(page.locator('#module-title')).toHaveText(name);await expect(row().locator('h3')).toHaveText(title+' modifié');
  await page.locator(selector('delete-'+id)).click();await page.locator('#delete-cancel').click();await expect(row()).toBeVisible();await page.locator(selector('delete-'+id)).click();await page.locator('#delete-confirm').click();await expect(row()).toHaveCount(0);
 }
 const taskId=await add(page,'Administratif','DEMO échéance'),projectId=await add(page,'Projets','DEMO projet');
 await page.locator('#nav-calendar').click();await page.locator(selector('month-next')).click();await page.locator(selector('month-previous')).click();await page.locator(selector('month-today')).click();await page.locator(selector('day-'+new Date().getDate())).click();await page.locator(selector('deadline-open-'+taskId)).click();await expect(page.locator('#module-title')).toHaveText('Administratif');await page.locator('#back').click();await expect(page.locator('#module-title')).toHaveText('Calendrier');
 await page.locator('#nav-statistics').click();await expect(page.locator(selector('statistics-summary'))).toContainText('1 projets');await page.locator(selector('stats-open-'+projectId)).click();await expect(page.locator('#module-title')).toHaveText('Projets');
 for(const name of ['Administratif','Création']){
  await modulePage(page,name);await page.locator('#file-input').setInputFiles({name:'unsupported.exe',mimeType:'application/octet-stream',buffer:Buffer.from('NO')});await expect(page.locator('#message')).toContainText('non compatible');
  await page.locator('#file-input').setInputFiles({name:'demo.txt',mimeType:'text/plain',buffer:Buffer.from('DEMO FILE CONTENT')});await expect(page.locator('#message')).toContainText('Fichier conservé');const fileRow=page.locator('.record').filter({has:page.locator('h3',{hasText:'demo.txt'})}).first();const id=await fileRow.getAttribute('data-entry-id');let promise=page.waitForEvent('download');await page.locator(selector('file-open-'+id)).click();let download=await promise;expect(fs.readFileSync(await download.path(),'utf8')).toBe('DEMO FILE CONTENT');
  await page.locator('#nav-documents').click();promise=page.waitForEvent('download');await page.locator(selector('document-open-'+id)).click();download=await promise;expect(fs.readFileSync(await download.path(),'utf8')).toBe('DEMO FILE CONTENT');await page.locator(selector('documents-import')).click();await expect(page.locator('#module-title')).toHaveText('Administratif');
 }
});

test('standalone file is interactive offline and persists after closing the page',async({browser})=>{
 const context=await browser.newContext({offline:true});let page=await context.newPage();let remoteRequests=[];page.on('request',r=>{if(/^https?:/.test(r.url()))remoteRequests.push(r.url());});
 const url='file://'+path.resolve(__dirname,'jarvis-ui01c.html');await page.goto(url);await add(page,'Administratif','DEMO OFFLINE');await page.locator('#nav-settings').click();await page.locator('#glow').uncheck();await page.locator('#hour').fill('5');await page.locator('#hour').dispatchEvent('input');await page.locator('#weather').selectOption('FOG');
 await page.close();page=await context.newPage();await page.goto(url);await expect(page.locator('#module-title')).toHaveText('Paramètres');await expect(page.locator('#glow')).not.toBeChecked();await expect(page.locator('#weather')).toHaveValue('FOG');await modulePage(page,'Administratif');await expect(page.locator('#records')).toContainText('DEMO OFFLINE');expect(remoteRequests).toEqual([]);await context.close();
});
