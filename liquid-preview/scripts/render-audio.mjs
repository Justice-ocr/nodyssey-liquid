import {mkdirSync,writeFileSync} from 'node:fs';
const rate=24000;
function cue(kind,dark){
 const duration={pour:.18,splash:.26,flow:1,finish:.16}[kind],out=new Float32Array(Math.round(duration*rate));
 let seed=1729,filtered=0;const pitch=dark?.74:1.18;
 for(let i=0;i<out.length;i++){
  const t=i/rate;seed=(Math.imul(seed,1664525)+1013904223)|0;filtered+=.085*(seed/2147483648-filtered);
  const edge=Math.max(0,Math.min(1,t/.012,(duration-t)/.035));
  let v=0;
  if(kind==='pour')v=(Math.sin(2*Math.PI*pitch*(520*t-750*t*t))*Math.exp(-25*t)*.22+filtered*Math.exp(-14*t)*.4)*edge;
  if(kind==='splash')v=(filtered*.9+Math.sin(2*Math.PI*170*pitch*t)*.07)*Math.exp(-15*t)*edge;
  if(kind==='flow')v=filtered*.28*(.75+.25*Math.sin(2*Math.PI*3*t))*edge;
  if(kind==='finish')v=Math.sin(2*Math.PI*(dark?270:520)*t)*Math.exp(-30*t)*.13*edge;
  out[i]=Math.tanh(v)*.25;
 }
 return out;
}
mkdirSync('public/audio',{recursive:true});
for(const dark of [false,true]){
 const duration=2.9,pcm=new Float32Array(Math.round(duration*rate));
 for(const [kind,at] of [['pour',0],['splash',.572],['flow',.728],['flow',1.688],['finish',2.6]]){
  const c=cue(kind,dark),offset=Math.round(at*rate);
  for(let i=0;i<c.length&&offset+i<pcm.length;i++)pcm[offset+i]+=c[i];
 }
 const wav=Buffer.alloc(44+pcm.length*2);wav.write('RIFF');wav.writeUInt32LE(wav.length-8,4);wav.write('WAVEfmt ',8);wav.writeUInt32LE(16,16);wav.writeUInt16LE(1,20);wav.writeUInt16LE(1,22);wav.writeUInt32LE(rate,24);wav.writeUInt32LE(rate*2,28);wav.writeUInt16LE(2,32);wav.writeUInt16LE(16,34);wav.write('data',36);wav.writeUInt32LE(pcm.length*2,40);
 for(let i=0;i<pcm.length;i++)wav.writeInt16LE(Math.max(-32767,Math.min(32767,Math.round(pcm[i]*32767))),44+i*2);
 writeFileSync(`public/audio/${dark?'into-dark':'into-light'}.wav`,wav);
}
console.log('Generated two original local PCM audio samples (2.9 seconds, 25% volume).');
