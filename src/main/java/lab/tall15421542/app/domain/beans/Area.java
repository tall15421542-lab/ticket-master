package lab.tall15421542.app.domain.beans;

public class Area {
    private int price;
    private int rows;
    private int cols;

    public Area(){

    }

    public Area(int price, int rows, int cols){
        this.price = price;
        this.rows = rows;
        this.cols = cols;
    }

    public int getPrice() {
        return price;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    public void setCols(int cols) {
        this.cols = cols;
    }
}
