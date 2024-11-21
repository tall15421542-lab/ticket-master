package lab.tall15421542.app.domain.beans;

import lab.tall15421542.app.avro.event.Area;

public class AreaBean {
    private String areaId;
    private int price;
    private int rowCount;
    private int colCount;

    public AreaBean(){

    }

    public AreaBean(String areaId, int price, int rowCount, int colCount){
        this.areaId = areaId;
        this.price = price;
        this.rowCount = rowCount;
        this.colCount = colCount;
    }

    public String getAreaId() { return areaId; }

    public int getPrice() {
        return price;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getColCount() {
        return colCount;
    }

    public void setAreaId(String areaId) { this.areaId = areaId; }

    public void setPrice(int price) {
        this.price = price;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public void setColCount(int colCount) {
        this.colCount = colCount;
    }

    public Area toAvro(){
        return new Area(this.areaId, this.price, this.rowCount, this.colCount);
    }
}
