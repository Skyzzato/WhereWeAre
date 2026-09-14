import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
for(const name of ['flare_launch','flare_burst','flare_notification']) {
  const file=`app/src/main/res/raw/${name}.wav`, b=readFileSync(file);
  if(b.toString('ascii',0,4)!=='RIFF'||b.toString('ascii',8,12)!=='WAVE') throw Error(file);
  let format,data;
  for(let i=12;i+8<=b.length;) {
    const n=b.readUInt32LE(i+4),tag=b.toString('ascii',i,i+4);
    if(tag==='fmt ') format={pcm:b.readUInt16LE(i+8),channels:b.readUInt16LE(i+10),rate:b.readUInt32LE(i+12),bits:b.readUInt16LE(i+22)};
    if(tag==='data') data=b.subarray(i+8,i+8+n);
    i+=8+n+(n%2);
  }
  if(!format||!data||format.pcm!==1||format.bits!==16) throw Error('Unsupported or damaged WAV: '+file);
  let power=0,peak=0;
  for(let i=0;i<data.length;i+=2) {const v=data.readInt16LE(i)/32768;power+=v*v;peak=Math.max(peak,Math.abs(v));}
  const rms=Math.sqrt(power/(data.length/2));
  if(rms<.001) throw Error('Effect is effectively silent: '+file);
  console.log(JSON.stringify({name,...format,seconds:data.length/(format.rate*format.channels*2),rms,peak,sha256:createHash('sha256').update(b).digest('hex')}));
}
