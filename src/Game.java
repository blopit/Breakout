import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.*;

import java.lang.*;

public class Game extends JPanel implements Runnable {
	private static final long serialVersionUID = -9L;
	boolean running = true;
	Thread animator;

	int totalFrameCount = 0;
	int current_fps = 0;
	ActionListener updateFPS = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent event) {
			current_fps = totalFrameCount;
			totalFrameCount = 0;
		}
	};

	double lastUpdateTime = System.nanoTime();
	private int pos_x = 0;
	private int pos_y = 0;

	private Ball ball;
	private Paddle paddle;
	private int screen_width = 800;
	private int screen_height = 600;
	private static ArrayList<Block> blockList;

	public void addNotify() {
		super.addNotify();
		animator = new Thread(this);
		animator.start();
	}

	private float dist(double d, double e, double camx2, double camy2) {
		return (float) Math.sqrt(Math.pow(camx2-d,2) + Math.pow(camy2-e,2));
	}
	
	private boolean collisionRectRect(double AX1, double AY1, double AX2, double AY2,
			double BX1, double BY1, double BX2, double BY2) {
		return (AX1 < BX2 && AX2 > BX1 && AY1 < BY2 && AY2 > BY1);
	}

	private boolean collisionCircleLine(double circleCenterX, double circleCenterY,
			double circleRadius, double lineAX, double lineAY, double lineBX,
			double lineBY) {

		/*
		 * if
		 * (!collisionRectRect(b.x-b.radius,b.y-b.radius,b.x+b.radius,b.y+b.radius
		 * , lineAX,lineAY,lineBX,lineBY)) return false;
		 */

		// double circleCenterX = b.x;
		// double circleCenterY = b.y;
		// double circleRadius = b.radius;

		double lineSize = Math.sqrt(Math.pow(lineAX - lineBX, 2)
				+ Math.pow(lineAY - lineBY, 2));
		double distance;

		if (lineSize == 0) {
			distance = Math.sqrt(Math.pow(circleCenterX - lineAX, 2)
					+ Math.pow(circleCenterY - lineAY, 2));
			return distance < circleRadius;
		}

		double u = ((circleCenterX - lineAX) * (lineBX - lineAX) + (circleCenterY - lineAY)
				* (lineBY - lineAY))
				/ (lineSize * lineSize);

		if (u < 0) {
			distance = Math.sqrt(Math.pow(circleCenterX - lineAX, 2)
					+ Math.pow(circleCenterY - lineAY, 2));
		} else if (u > 1) {
			distance = Math.sqrt(Math.pow(circleCenterX - lineBX, 2)
					+ Math.pow(circleCenterY - lineBY, 2));
		} else {
			double ix = lineAX + u * (lineBX - lineAX);
			double iy = lineAY + u * (lineBY - lineAY);
			distance = Math.sqrt(Math.pow(circleCenterX - ix, 2)
					+ Math.pow(circleCenterY - iy, 2));
		}

		return distance < circleRadius;
	}

	private class Line {
		private int AX;
		private int AY;
		private int BX;
		private int BY;
		private Block rect;
		private class Point {
			   double x;
			   double y;
			   public Point(double d, double e){
				   this.x = d;
				   this.y = e;
			   }
		}
		public Line(Block r, int ax, int ay, int bx, int by) {
			
			Point A = rotateAbout(new Point(ax,ay),new Point(r.x+r.width/2,r.y+r.height/2),r.angle);
			Point B = rotateAbout(new Point(bx,by),new Point(r.x+r.width/2,r.y+r.height/2),r.angle);
			
			AX = (int) A.x;
			AY = (int) A.y;
			BX = (int) B.x;
			BY = (int) B.y;
			rect = r;
		}
		private Point rotateAbout(Point p, Point c, double theta){
			double tempX = p.x - c.x;
			double tempY = p.y - c.y;
			double rotatedX = tempX*Math.cos(theta) - tempY*Math.sin(theta);
			double rotatedY = tempX*Math.sin(theta) + tempY*Math.cos(theta);
			tempX = rotatedX + c.x;
			tempY = rotatedY + c.y;
			return new Point(tempX, tempY);
		}
	}

	private class Rect {
		protected int x;
		protected int y;
		protected int width;
		protected int height;
		protected double angle;
		
		public Rect(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.angle = 0;
		}
		
		public Rect(int x, int y, int width, int height, double a) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.angle = a;
		}
	}

	private class Block extends Rect {
		private int hp;
		protected Line[] lines;
		
		public int minX = this.x;
		public int maxX = this.x+this.width;
		public int minY = this.y;
		public int maxY = this.y+this.height;
		
		public void hit(){
			this.hp--;
			if (this.hp <= 0){
				blockList.remove(this);
			}
		}
		
		public Block(int x, int y, int width, int height, int hp, double a) {
			super(x, y, width, height, a);
			this.hp = hp;
			this.lines = new Line[] {
					new Line(this, this.x, this.y, this.x + this.width, this.y),
					new Line(this,this.x + this.width, this.y, this.x + this.width,
							this.y + this.height),
					new Line(this,this.x + this.width, this.y + this.height, this.x,
							this.y + this.height),
					new Line(this,this.x, this.y + this.height, this.x, this.y)};
			
			for (Line l: this.lines){
				if (l.AX < this.minX)
					this.minX = l.AX;
				else if (l.AX > this.maxX)
					this.maxX = l.AX;
				if (l.AY < this.minY)
					this.minY = l.AY;
				else if (l.AY > this.maxY)
					this.maxY = l.AY;
			}
		}
	}

	private class Paddle extends Rect {
		private int hsp;

		public Paddle(int x, int y, int width, int height) {
			super(x, y, width, height);
			this.hsp = 32;
		}

		private void moveTo(int dx) {

			if (Math.abs(dx - this.x) > this.hsp) {
				this.x += this.hsp * Math.signum((double) (dx - this.x));
			} else {
				this.x = dx;
			}
		}
	}
	
	private class Ball {
		private double x;
		private double y;
		private double xsp;
		private double ysp;
		private int radius;

		public Ball(double xx, double yy) {
			this.x = xx;
			this.y = yy;
			this.radius = 4;

			this.xsp = 3;
			this.ysp = -4;
		}
		
		private void transformDirection(double N){
			double c_speed = dist(0,0,this.xsp,this.ysp);
			double c_angle = Math.atan2(this.ysp,this.xsp);
			double e_angle = 2*N-c_angle-Math.PI;
			
			this.xsp = c_speed*Math.cos(e_angle);
			this.ysp = c_speed*Math.sin(e_angle);
			
		}
		
		private void setDirection(double N){
			double c_speed = dist(0,0,this.xsp,this.ysp);
			double e_angle = N;
			
			this.xsp = c_speed*Math.cos(e_angle);
			this.ysp = c_speed*Math.sin(e_angle);
		}
		
		private Line checkPlace(double cx, double cy) {
			for (Block b : blockList) {
				if (collisionRectRect(this.x - this.radius + cx, this.y
						- this.radius + cy, this.x + this.radius + cx, this.y
						+ this.radius + cy, b.minX, b.minY, b.maxX, b.maxY)) {
					for (Line l : b.lines) {
						if (collisionCircleLine(this.x + cx, this.y + cy,
								this.radius, l.AX, l.AY, l.BX, l.BY)) {
							return l;
						}
					}
				}
			}
			return null;
		}
		
		private void bounce(Line l){
			transformDirection(-Math.PI/2+Math.atan2(l.BY-l.AY, l.BX-l.AX));
			l.rect.hit();
		}

		private void update() {
			if (collisionCircleLine(ball.x, ball.y, ball.radius, paddle.x,
					paddle.y, paddle.x + paddle.width, paddle.y)) {
				//transformDirection;
				setDirection((-90+60*(ball.x-(paddle.x+paddle.width/2))/paddle.width)*Math.PI/180);
			}

			if (this.x + this.radius > screen_width && this.xsp > 0) {
				this.xsp *= -1;
			} else if (this.x - this.radius < 0 && this.xsp < 0) {
				this.xsp *= -1;
			} else if (this.y - this.radius < 0 && this.ysp < 0) {
				this.ysp *= -1;
			} else if (this.y + this.radius > screen_height && this.ysp > 0) {
				// game over
			}
			
			Line tempL = null;
			int mult = 1;
			
			for (int i=0;i<mult;i++)
			{
				tempL = checkPlace(xsp/mult, ysp/mult);
				if (tempL != null) {
					bounce(tempL);
				}else{
					x+=xsp/mult;
					y+=ysp/mult;
				}
			}
			
			
			/*int mult = 1;
			Line tempL = null;
			if (this.xsp > 0) {
				for (int i = 0; i < this.xsp*mult; i++) {
					tempL = checkPlace(1.0/mult, 0);
					if (tempL != null) {
						bounce(tempL);
						break;
					}
					this.x+=1/mult;
				}
			} else if (this.xsp < 0) {
				for (int i = 0; i < -this.xsp*mult; i++) {
					tempL = checkPlace(-1.0/mult, 0);
					if (tempL != null) {
						bounce(tempL);
						break;
					}
					this.x-=1/mult;
				}
			}
			if (this.ysp > 0) {
				for (int i = 0; i < this.ysp*mult; i++) {
					tempL = checkPlace(0, 1.0/mult);
					if (tempL != null) {
						bounce(tempL);
						break;
					}
					this.y+=1/mult;
				}
			} else if (this.ysp < 0) {
				for (int i = 0; i < -this.ysp*mult; i++) {
					tempL = checkPlace(0, -1.0/mult);
					if (tempL != null) {
						bounce(tempL);
						break;
					}
					this.y-=1/mult;
				}
			}*/

		}
	}

	public Game() {
		BufferedImage cursorImg = new BufferedImage(16, 16,
				BufferedImage.TYPE_INT_ARGB);
		Cursor blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
				cursorImg, new Point(0, 0), "blank cursor");
		this.setCursor(blankCursor);

		this.setFocusable(true);

		this.addMouseMotionListener(new ML());
		this.addKeyListener(new KL());
		blockList = new ArrayList<Block>();

		Timer t = new Timer(1000, updateFPS);
		t.setInitialDelay(0);
		t.start();

		ball = new Ball(128, 500);
		paddle = new Paddle(256, 540, 64, 8);

		for (int j = 1; j < 3; j++) {
			for (int i = 0; i < 10; i++) {
				Block block = new Block(32 + i * 64, 32 + j * 65, 64, 64, j , (25*Math.sin((0.0+i*50.0)/180*Math.PI)/180)*Math.PI);
				blockList.add(block);
			}
		}

	}

	public void run() {

		long last_loop = System.nanoTime();
		final int target_fps = 60;
		final long optimal_time = 1000000000 / target_fps;

		while (running) {

			long now = System.nanoTime();
			last_loop = now;

			update();
			repaint();

			try {
				Thread.sleep((last_loop - System.nanoTime() + optimal_time) / 1000000);
			} catch (InterruptedException e) {
				System.out.println("interrupted");
			}
			lastUpdateTime = System.currentTimeMillis();
		}
	}

	public void update() {
		paddle.moveTo(pos_x - paddle.width / 2);
		ball.update();
		totalFrameCount++;
	}

	public void paint(Graphics g) {
		super.paint(g);
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		Font font = new Font("Serif", Font.PLAIN, 14);
		g2.setFont(font);

		g2.drawRect(pos_x, pos_y, 64, 64);
		screen_width = this.getWidth();
		screen_height = this.getHeight();

		g2.setColor(Color.BLACK);
		g2.fillRect(0, 0, screen_width, screen_height);

		if (blockList.size() > 0) {
			for (Block b : blockList) {
				switch (b.hp) {
				case 1:
					g2.setColor(Color.RED);
					break;
				case 2:
					g2.setColor(Color.ORANGE);
					break;
				case 3:
					g2.setColor(Color.YELLOW);
					break;
				case 4:
					g2.setColor(Color.GREEN);
					break;
				case 5:
					g2.setColor(Color.BLUE);
					break;
				case 6:
					g2.setColor(Color.MAGENTA);
					break;
				case 7:
					g2.setColor(Color.lightGray);
					break;
				default:
					g2.setColor(Color.WHITE);
					break;
				}
				Graphics2D gg = (Graphics2D) g2.create();
				gg.translate(b.x+b.width/2, b.y+b.height/2);
				gg.rotate(b.angle);
				gg.fillRect(-b.width/2, -b.height/2, b.width, b.height);
				gg.dispose();
				
				g2.setColor(Color.PINK);
				for (Line l: b.lines) {
					g2.drawLine(l.AX, l.AY, l.BX, l.BY);
				}
				//g2.drawRect(b.minX, b.minY, b.maxX-b.minX, b.maxY-b.minY);
			}
		}

		g2.setColor(Color.WHITE);
		g2.fillRect(paddle.x, paddle.y, paddle.width, paddle.height);
		g2.fillOval((int) (ball.x - ball.radius), (int)(ball.y - ball.radius),
				2 * ball.radius, 2 * ball.radius);
		g2.drawString("FPS: " + String.valueOf(current_fps), 8, 18);

	}

	public static void main(String args[]) {
		JFrame window = new JFrame("Breakout");
		window.setLocationByPlatform(true);
		window.setSize(800, 600);
		window.setContentPane(new Game());
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setVisible(true);
	}

	public class ML extends MouseMotionAdapter {
		public void mouseMoved(MouseEvent e) {
			pos_x = e.getX();
			pos_y = e.getY();
		}
	}

	public class KL extends KeyAdapter {
		public void keyPressed(KeyEvent e) {
			int key = e.getKeyCode();

			if (key == KeyEvent.VK_LEFT) {
				paddle.moveTo(paddle.x - paddle.hsp);
			} else if (key == KeyEvent.VK_RIGHT) {
				paddle.moveTo(paddle.x + paddle.hsp);
			}

		}
	}

}
