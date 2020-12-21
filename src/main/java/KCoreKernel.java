import com.aparapi.Kernel;
import com.aparapi.Range;

public class KCoreKernel extends Kernel {
	private int adjListV[];
	private int kCore[];
	private int degrees[];
	private int temp[];
	private int result[];
	private Range range;

	public KCoreKernel(Range range) {
		this.range = range;
		temp = new int[this.range.getGlobalSize(0)];
	}

	@Override
	public void run() {
		int index = getGlobalId();
		
		if (kCore[adjListV[index]] == 0) {
			degrees[adjListV[index]] = degrees[adjListV[index]] - 1;
			temp[index] = adjListV[index];
		} else {
			temp[index] = -1;
		}

	}

	public int[] getAdjListV() {
		return adjListV;
	}

	public void setAdjListV(int[] adjListV) {
		this.adjListV = adjListV;
	}

	public int[] getkCore() {
		return kCore;
	}

	public void setkCore(int[] kCore) {
		this.kCore = kCore;
	}

	public int[] getDegrees() {
		return degrees;
	}

	public void setDegrees(int[] degrees) {
		this.degrees = degrees;
	}

	public int[] getResult() {
		int count = 0;
		for (int i = 0; i < temp.length; i++) {
			if (temp[i] != -1) {
				count++;
			}
		}
		result = new int[count];
		count = 0;
		for (int i = 0; i < temp.length; i++) {
			if (temp[i] != -1) {
				result[count] = temp[i];
				count++;
			}
		}
		return result;
	}
}
