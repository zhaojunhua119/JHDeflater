import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class JHMyDeflater {
	private static Map<MyBitSet,String> bit2Str;
	private static Map<String,MyBitSet> str2Bit;
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		
		switch( args[0]){
		case "-gen":
			String dict=generateDict(FileSystems.getDefault().getPath(args[1]));
			Files.write(FileSystems.getDefault().getPath(args[1]+".dict"), dict.getBytes());
			break;
		case "-c":
			bit2Str=new HashMap<MyBitSet,String>();
			str2Bit=new HashMap<String,MyBitSet>();
			readDictFile(FileSystems.getDefault().getPath(args[1]),bit2Str,str2Bit);
			compressFile(FileSystems.getDefault().getPath(args[2]),FileSystems.getDefault().getPath(args[3]));
			break;
		case "-d":
			bit2Str=new HashMap<MyBitSet,String>();
			str2Bit=new HashMap<String,MyBitSet>();
			readDictFile(FileSystems.getDefault().getPath(args[1]),bit2Str,str2Bit);
			decompressFile(FileSystems.getDefault().getPath(args[2]),FileSystems.getDefault().getPath(args[3]));
			break;
			
		}
	}
	private static class WordSeqNode {
		public WordSeqNode left=null;
		public WordSeqNode right=null;
		public int freq=0;
		public String wordSeq=null;
		public String huffmanCode="";
	}
	private static class MyBitSet extends BitSet  {
		@Override
		public void clear() {
			// TODO Auto-generated method stub
			length=0;
			super.clear();
		}

		public int length;

		@Override
		public boolean equals(Object obj) {
			if(this.length!=((MyBitSet)obj).length)
				return false;
			return super.equals(obj);
		}

		@Override
		public String toString() {
			// TODO Auto-generated method stub
			StringBuilder sb=new StringBuilder();
			for(int i=0;i<length;i++){
				sb.append(this.get(i)?"1":"0");
			}
			return sb.toString()+super.toString();
		}
		
		
	}
	
	private static class WordSeqNodeComparator implements Comparator<WordSeqNode>{

		@Override
		public int compare(WordSeqNode o1, WordSeqNode o2) {
			if(o1.freq>o2.freq)
				return 1;
			else if(o1.freq<o2.freq)
				return -1;
			else
				return 0;
		}
	}
	private static void setBits(MyBitSet bsSrc,MyBitSet bsDst,int startDst){
		for(int i=0;i<bsSrc.length;i++)
		{
			bsDst.set(startDst+i,bsSrc.get(i));
			bsDst.length++;
		}
	}
	private static void setBits(byte[] bySrc,int length,MyBitSet bsDst,int startDst){
		for(int i=0;i<length;i++)
		{
			bsDst.set(startDst+i,(bySrc[i/8]&1<<i%8)>0);
			bsDst.length++;
		}
	}
	
	private static void getBits(MyBitSet bsSrc,MyBitSet bsDst,int startDst,int len){
		bsDst.length=0;
		bsDst.clear();
		for(int i=0;i<len;i++)
		{
			if(startDst+i>=bsSrc.length)
				break;
			boolean bit=bsSrc.get(startDst+i);
			bsDst.set(i,bit);
			bsDst.length++;
		}
	}
	private static int getBit(MyBitSet bsSrc,int startDst){
		if(startDst>=bsSrc.length)
			return -1;
		boolean bit=bsSrc.get(startDst);
		return bit?1:0;
	}
	private static void writeBitsWithRawStr(String src,MyBitSet bsDst,int startDst){
		bsDst.length=startDst;
		for(byte by:src.getBytes()){
			bsDst.set(bsDst.length,false);
			bsDst.length++;
			BitSet bs=BitSet.valueOf((new byte[]{by}));
			for(int i=0;i<8;i++){
				bsDst.set(bsDst.length,bs.get(i));
				bsDst.length++;
			}
		}
	}
	
	static MyBitSet testBitSet_bsCurrent=null;
	private static boolean testBitSet(MyBitSet bsSrc,MyBitSet bsCmp,int startPos){
		
		if(testBitSet_bsCurrent==null){
			testBitSet_bsCurrent=new MyBitSet();
		}
		testBitSet_bsCurrent.clear();
		getBits(bsSrc,testBitSet_bsCurrent,startPos,bsCmp.length);
		return testBitSet_bsCurrent.equals(bsCmp);
	}
	static MyBitSet isNewLine_bsNewLine=null;
	private static boolean isNewLine(MyBitSet bsSrc,int startPos){
		if(isNewLine_bsNewLine==null){
			isNewLine_bsNewLine= new MyBitSet();
			isNewLine_bsNewLine.set(0,false);
			isNewLine_bsNewLine.length++;
			setBits("\n".getBytes(),8,isNewLine_bsNewLine,1);
		}
		return testBitSet(bsSrc,isNewLine_bsNewLine,startPos);
	}
	static MyBitSet isEOF_bsEOF=null;
	private static boolean isEOF(MyBitSet bsSrc,int startPos){
		if(isEOF_bsEOF==null){
			isEOF_bsEOF= new MyBitSet();
			isEOF_bsEOF.set(0,true);
			isEOF_bsEOF.length++;
			setBits(str2Bit.get("EOFEOFEOF"),isEOF_bsEOF,1);
		}
		return testBitSet(bsSrc,isEOF_bsEOF,startPos);
	}
	private static void decompressFile(Path inPath,Path outPath) throws IOException{
		byte[] srcBytes=Files.readAllBytes(inPath);
		Files.deleteIfExists(outPath);
		Files.createFile(outPath);
		MyBitSet bsSrc=new MyBitSet();
		bsSrc.or(BitSet.valueOf(srcBytes));
		bsSrc.length=srcBytes.length*8;
		MyBitSet currentCode=new MyBitSet();
		int current=0;
		int value=0;
		outer: while(true){
			if(isEOF(bsSrc,current))
				break;
			int flag=getBit(bsSrc,current);
			current++;
			currentCode.clear();
			if(flag==1){
				inner: for(int i=0;i<20;i++){
					value=getBit(bsSrc,current);
					current++;
					if(value==-1)
						break outer;
					
					currentCode.set(i,value==1);
					currentCode.length=i+1;
					if(bit2Str.containsKey(currentCode)){
						String str=bit2Str.get(currentCode);
						System.out.print(str);
						Files.write(outPath, str.getBytes(), StandardOpenOption.APPEND);
						if(!isNewLine(bsSrc,current)&&!isEOF(bsSrc,current)){
							System.out.print(",");
							Files.write(outPath, ",".getBytes(), StandardOpenOption.APPEND);
						}
						break inner;
					}
					 
				}
			}else if (flag==0){
				getBits(bsSrc,currentCode,current,8);
				current+=8;
				System.out.print(new String(currentCode.toByteArray()));
				Files.write(outPath, currentCode.toByteArray(), StandardOpenOption.APPEND);
				
			}
			else{break outer;}
		}
	}
	private static void compressFile(Path inPath,Path outPath) throws IOException{
		List<String> lines=Files.readAllLines(inPath, Charset.defaultCharset());
		MyBitSet compressed=new MyBitSet();
		for(int l=0;l<lines.size();l++){
			String line=lines.get(l);
			String[] wordSeqs=line.split(",");
			for(int i=0;i<wordSeqs.length;i++){
				String wordSeq=wordSeqs[i];
				
				if(str2Bit.containsKey(wordSeq)){
					compressed.set(compressed.length,true);compressed.length++;
					setBits(str2Bit.get(wordSeq),compressed,compressed.length);
				}else{
					writeBitsWithRawStr(wordSeq,compressed,compressed.length);
					if(i!=wordSeqs.length-1)
						writeBitsWithRawStr(",",compressed,compressed.length);
				}
				
			}
			if(l!=lines.size()-1)
				writeBitsWithRawStr("\n",compressed,compressed.length);
		}
		compressed.set(compressed.length,true);compressed.length++;
		setBits(str2Bit.get("EOFEOFEOF"),compressed,compressed.length);
		Files.write(outPath, compressed.toByteArray());
		
	}
	private static void readDictFile(Path path,Map<MyBitSet,String> bit2Str,Map<String,MyBitSet> str2Bit) throws IOException{
		List<String> lines=Files.readAllLines(path, Charset.defaultCharset());
		for(String line : lines){
			String[] sp=line.split(",");
			MyBitSet bs=new MyBitSet(); 
			int i=0;
			for(char ch : sp[0].toCharArray()){
				bs.set(i,ch=='1');
				bs.length++;
				i++;
			}
			if(sp.length==1){
				sp=new String[]{sp[0],""};
			}
			bit2Str.put(bs, sp[1]);
			str2Bit.put(sp[1],bs);
		}
	}
	private static String generateDict(Path path) throws IOException{
		List<String> lines=Files.readAllLines(path, Charset.defaultCharset());
		Map<String,Integer> wordSeqFreq = generateWordSeqFreq(lines);
		wordSeqFreq.put("EOFEOFEOF", 1);
		PriorityQueue<WordSeqNode> wordSeqFreqPQ=new PriorityQueue<WordSeqNode>(1,new WordSeqNodeComparator());
		for(Entry<String, Integer> es:wordSeqFreq.entrySet()){
			WordSeqNode wsNode=new WordSeqNode();
			wsNode.wordSeq=es.getKey();
			wsNode.freq=es.getValue();
			wordSeqFreqPQ.add(wsNode);
		}
		WordSeqNode rootHuffman=buildHuffman(wordSeqFreqPQ);
		return BFSDumpHuffman(rootHuffman);
		
		
	}
	private static String BFSDumpHuffman(WordSeqNode root){
		Queue<WordSeqNode> queue=new LinkedList<WordSeqNode>();
		root.huffmanCode="";
		StringBuilder sb=new StringBuilder();
		queue.add(root);
		while(queue.size()>0){
			WordSeqNode current=queue.poll();
			if(current.wordSeq!=null){
				String map=String.format("%s,%s",current.huffmanCode,current.wordSeq);
				System.out.println(map);
				sb.append(map+"\n");
			}
			WordSeqNode left=current.left;
			if(left!=null){
				left.huffmanCode=current.huffmanCode+"0";
				queue.add(left);
			}
			WordSeqNode right=current.right;
			if(right!=null){
				right.huffmanCode=current.huffmanCode+"1";
				queue.add(right);
			}
		}
		return sb.toString();
	}
	private static WordSeqNode buildHuffman(PriorityQueue<WordSeqNode> wordSeqFreqPQ){
		while(wordSeqFreqPQ.size()>2){
			WordSeqNode n1=wordSeqFreqPQ.poll();
			WordSeqNode n2=wordSeqFreqPQ.poll();
			WordSeqNode nNew=new WordSeqNode();
			nNew.left=n1;
			nNew.right=n2;
			nNew.freq=n1.freq+n2.freq;
			wordSeqFreqPQ.add(nNew);
		}
		WordSeqNode root=new WordSeqNode();
		root.left=wordSeqFreqPQ.poll();
		root.right=wordSeqFreqPQ.poll();
		return root;
	}
	private static Map<String,Integer> generateWordSeqFreq(List<String> lines){
		Map<String,Integer> wordSeqFreqStr=new HashMap<String,Integer>();
		for(String line: lines){
			for(String wordSeq :line.split(",")){
				if(!wordSeqFreqStr.containsKey(wordSeq))
					wordSeqFreqStr.put(wordSeq, 1);
				else
					wordSeqFreqStr.put(wordSeq,wordSeqFreqStr.get(wordSeq)+1);
			}
		}
		return wordSeqFreqStr;
	}
	

}