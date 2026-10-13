import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import Database from 'better-sqlite3';
import { TrialStore } from '../src/store.js';
import { backupTrialDatabase } from '../deploy/backup-trial-db.mjs';

test('SQLite backup is restorable and preserves the first trial activation',async()=>{
  const root=mkdtempSync(join(tmpdir(),'prompt-trial-backup-'));
  const source=join(root,'source.sqlite');
  const backups=join(root,'backups');
  try{
    const store=new TrialStore(source,()=>2_100_000_000_000);
    store.signIn('backup-subject');
    store.close();
    const backup=await backupTrialDatabase(source,backups,new Date('2026-10-13T00:00:00.000Z'));
    assert.ok(existsSync(backup));
    const restored=new Database(backup,{readonly:true});
    assert.equal(restored.pragma('integrity_check',{simple:true}),'ok');
    assert.equal(restored.prepare('SELECT google_sub FROM users').get().google_sub,'backup-subject');
    restored.close();
  }finally{rmSync(root,{recursive:true,force:true});}
});
