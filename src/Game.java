import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import javax.swing.*;


public class Game extends JPanel{
	
	int totalFrameCount = 0;
	ActionListener updateFPS = new ActionListener()
    {   
        @Override
        public void actionPerformed(ActionEvent event)
        {
	        System.out.println(totalFrameCount);
	        totalFrameCount = 0;
	    }
	};
	
	
	double lastUpdateTime = System.nanoTime();
	private int pos_x = 0;
	private int pos_y = 0;
	private int fps = 60;
	private Timer timer;
	
	private Ball ball;
	private Paddle paddle;
	private int screen_width = 800;
	private int screen_height = 600;
	private static ArrayList<Rect> rectList;
	
	boolean collisionRectRect(double AX1 , double AY1, double AX2, double AY2, double BX1 , double BY1, double BX2, double BY2)
	{
		return (AX1 <= BX2 && AX2 >= BX1 &&
			    AY1 <= BY2 && AY2 >= BY1); 
	}
	
	boolean collisionCircleLine(Ball b, double lineAX, double lineAY, double lineBX, double lineBY) {
		
		if (!collisionRectRect(b.x-b.radius,b.y-b.radius,b.x+b.radius,b.y+b.radius,
				lineAX,lineAY,lineBX,lineBY))
			return false;
		
		double circleCenterX = b.x; 
		double circleCenterY = b.y; 
		double circleRadius = b.radius;
		
	    double lineSize = Math.sqrt(Math.pow(lineAX-lineBX, 2) + Math.pow(lineAY-lineBY, 2));
	    double distance;

	    if (lineSize == 0) {
	        distance = Math.sqrt(Math.pow(circleCenterX-lineAX, 2) + Math.pow(circleCenterY-lineAY, 2));
	        return distance < circleRadius;
	    }

	    double u = ((circleCenterX - lineAX) * (lineBX - lineAX) + (circleCenterY - lineAY) * (lineBY - lineAY)) / (lineSize * lineSize);

	    if (u < 0) {
	        distance = Math.sqrt(Math.pow(circleCenterX-lineAX, 2) + Math.pow(circleCenterY-lineAY, 2));
	    } else if (u > 1) {
	        distance = Math.sqrt(Math.pow(circleCenterX-lineBX, 2) + Math.pow(circleCenterY-lineBY, 2));
	    } else {
	        double ix = lineAX + u * (lineBX - lineAX);
	        double iy = lineAY + u * (lineBY - lineAY);
	        distance = Math.sqrt(Math.pow(circleCenterX-ix, 2) + Math.pow(circleCenterY-iy, 2));
	    }

	    return distance < circleRadius;
	}
	
	private class Rect {
		protected int x;
		protected int y;
		protected int width;
		protected int height;
		protected double rotation;
		
		public Rect(int x, int y, int width, int height){
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.rotation = 0;
		}
	}
	
	private class Block extends Rect{
		private int hp;
		public Block(int x, int y, int width, int height, int hp) {
			super(x, y, width, height);
			this.hp = hp;
		}
	}
	
	private class Paddle extends Rect{
		private int hsp;
		public Paddle(int x, int y, int width, int height) {
			super(x, y, width, height);
			this.hsp = 32;
		}
		private void moveTo(int dx){
			
			if (Math.abs(dx-this.x) > this.hsp){
				this.x += this.hsp*Math.signum((double)(dx-this.x));
			}else{
				this.x = dx;
			}
		}
	}
	
	private class Ball{
		private int x;
		private int y;
		private int xsp;
		private int ysp;
		private int radius;
		public Ball(int xx, int yy){
			this.x = xx;
			this.y = yy;
			this.radius = 4;
			
			this.xsp = 2;
			this.ysp = 2;
		}
		
		private void update(){
			if (collisionCircleLine(ball,paddle.x,paddle.y,paddle.x+paddle.width,paddle.y)){
				this.ysp *= -1;
			}
			
			if (this.x+this.radius > screen_width && this.xsp > 0) {
				this.xsp *= -1;
			}else if (this.x-this.radius < 0 && this.xsp < 0) {
				this.xsp *= -1;
			}else if (this.y-this.radius < 0 && this.ysp < 0) {
				this.ysp *= -1;
			}else if (this.y+this.radius > screen_height && this.ysp > 0) {
				//game over
			}
			
			this.x += this.xsp;
			this.y += this.ysp;
		}
	}
	
	public Game(){
		BufferedImage cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Cursor blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
		    cursorImg, new Point(0, 0), "blank cursor");
		this.setCursor(blankCursor);
		
		setFocusable(true);
        setDoubleBuffered(true);
        
        this.addMouseMotionListener(new ML());
        this.addKeyListener(new KL());
		
        timer = new Timer(0, loop);
        timer.setInitialDelay(1000);
        timer.start();
        
        Timer t = new Timer(1000, updateFPS);
    	t.setInitialDelay(0);
        t.start();
        
        ball = new Ball(128,128);
        paddle = new Paddle(256,540,64,8);
        lastUpdateTime = System.nanoTime();
        
	}
	
	ActionListener loop = new ActionListener()
    {   
        @Override
        public void actionPerformed(ActionEvent event)
        {
        	
        	//loop
        	paddle.moveTo(pos_x-paddle.width/2);
        	ball.update();
        	repaint();
        	totalFrameCount++;
        	
        	long l = (long) (((1000000000/fps) - (System.nanoTime() - lastUpdateTime))/1000000);
        	System.out.println(l);
        	try {
        		if (l > 0)
        			Thread.sleep(l);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	lastUpdateTime = System.nanoTime();
        	
        	
        	
        }
    };
	
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.drawRect(pos_x, pos_y, 64, 64);
        screen_width = this.getWidth();
    	screen_height = this.getHeight();
        
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, screen_width, screen_height);
        
        g2.setColor(Color.WHITE);
        g2.fillRect(paddle.x, paddle.y, paddle.width, paddle.height);
        g2.fillOval(ball.x-ball.radius, ball.y-ball.radius, 2*ball.radius, 2*ball.radius);
        
        g2.setColor(Color.BLUE);
        /*for (Rect r : rectList){
        	//g2.fillRect(r.x, r.y, r.width, r.height);
    	}*/
	}
	
	public static void main(String args[]){
		JFrame window = new JFrame("Test Containment");
		window.setSize(800, 600);
		window.setContentPane(new Game());
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setVisible(true);
	}
	
	public class ML extends MouseMotionAdapter
	{
		public void mouseMoved(MouseEvent e) {
			pos_x = e.getX();
			pos_y = e.getY();
		}
	}

	public class KL extends KeyAdapter
	{
		public void keyPressed(KeyEvent e) {
			int key = e.getKeyCode();
			
			if (key == KeyEvent.VK_LEFT) {
				paddle.moveTo(paddle.x-paddle.hsp);
			}else if (key == KeyEvent.VK_RIGHT) {
				paddle.moveTo(paddle.x+paddle.hsp);
			}
			
		}
	}
}
				