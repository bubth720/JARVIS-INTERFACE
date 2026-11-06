const {test,expect}=require('@playwright/test');
test('modules, local records, simulation and responsive layouts',async({page})=>{
 await page.goto('http://127.0.0.1:4173');
 for(const name of ['Système','Administratif','Finance','Projets','Écriture','Création','Technique','Sécurité']){
  await page.locator('[data-module]').filter({hasText:new RegExp('^'+name+'$')}).click();
  await expect(page.locator('#module-title')).toHaveText(name);
 }
 await page.locator('[data-module="Administratif"]').click();
 await page.locator('#title').fill('DÉMONSTRATION — tâche test');
 await page.locator('#body').fill('<img src=x onerror=alert(1)>');
 await page.locator('#entry-form button[type=submit]').click();
 await page.reload();await page.locator('[data-module="Administratif"]').click();
 await expect(page.locator('#records')).toContainText('DÉMONSTRATION — tâche test');
 await expect(page.locator('#records img')).toHaveCount(0);
 await page.locator('#hour').evaluate(el=>{el.value='22';el.dispatchEvent(new Event('input',{bubbles:true}));});
 await page.locator('#weather').selectOption('SNOW');
 await expect(page.locator('#ambience')).toContainText('NUIT');
 await expect(page.locator('#weather-art circle')).toHaveCount(33);
 for(const [name,width,height] of [['portrait',412,915],['landscape',1280,800]]){
  await page.setViewportSize({width,height});await page.locator('#home').click();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
  await page.screenshot({path:'test-results/html-'+name+'.png',fullPage:true});
 }
});
