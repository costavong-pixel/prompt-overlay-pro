import Database from 'better-sqlite3';
import { existsSync, mkdirSync, renameSync, rmSync } from 'node:fs';
import { basename, isAbsolute, join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

function absolute(value,name){
  if(typeof value!=='string'||!isAbsolute(value))
    throw new Error(name+' must be an absolute path');
  return resolve(value);
}

function stamp(now){
  return now.toISOString().replace(/[:.]/g,'-');
}

export async function backupTrialDatabase(sourcePath,destinationDir,now=new Date()){
  const source=absolute(sourcePath,'Source database');
  const destination=absolute(destinationDir,'Backup directory');
  if(!existsSync(source))throw new Error('Source database does not exist');
  mkdirSync(destination,{recursive:true,mode:0o700});
  const filename='entitlements-'+stamp(now)+'.sqlite';
  const finalPath=join(destination,filename);
  const temporaryPath=join(destination,'.'+filename+'.'+process.pid+'.tmp');
  const database=new Database(source,{readonly:true,fileMustExist:true});
  try{
    await database.backup(temporaryPath);
    renameSync(temporaryPath,finalPath);
    return finalPath;
  }catch(error){
    rmSync(temporaryPath,{force:true});
    throw error;
  }finally{database.close();}
}

async function main(){
  const [source,destination]=process.argv.slice(2);
  const output=await backupTrialDatabase(source,destination);
  console.log('Created SQLite backup '+basename(output));
}

if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  main().catch(error=>{
    console.error(error.message);
    process.exitCode=1;
  });
}
