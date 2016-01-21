import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;

import javax.swing.*;
import javax.swing.Timer;

public class Game extends JPanel implements Runnable {
	private static final long serialVersionUID = -9L;
	boolean running = true;
	Thread animator;

	int totalFrameCount = 0;
	int current_fps = 0;

	double lastUpdateTime = System.nanoTime();
	private int pos_x = 0;
	private int pos_y = 0;

	private Ball ball;
	private Paddle paddle;
	private int screen_width = 800;
	private int screen_height = 600;
	private ArrayList<Block> blockList;

	public void addNotify() {
		super.addNotify();
		animator = new Thread(this);
		animator.start();
	}

	ActionListener updateFPS = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent event) {
			current_fps = totalFrameCount;
			totalFrameCount = 0;
		}
	};

	public static Color blend(Color c0, Color c1, double weight1) {
		// double totalAlpha = c0.getAlpha() + c1.getAlpha();
		double weight0 = 1 - weight1;// c0.getAlpha() / totalAlpha;
		// double weight1 = c1.getAlpha() / totalAlpha;

		double r = weight0 * c0.getRed() + weight1 * c1.getRed();
		double g = weight0 * c0.getGreen() + weight1 * c1.getGreen();
		double b = weight0 * c0.getBlue() + weight1 * c1.getBlue();
		double a = Math.max(c0.getAlpha(), c1.getAlpha());

		return new Color((int) r, (int) g, (int) b, (int) a);
	}

	private float dist(double d, double e, double camx2, double camy2) {
		return (float) Math.sqrt(Math.pow(camx2 - d, 2)
				+ Math.pow(camy2 - e, 2));
	}

	private boolean collisionRectRect(double AX1, double AY1, double AX2,
			double AY2, double BX1, double BY1, double BX2, double BY2) {
		return (AX1 < BX2 && AX2 > BX1 && AY1 < BY2 && AY2 > BY1);
	}

	private boolean collisionCircleLine(double circleCenterX,
			double circleCenterY, double circleRadius, double lineAX,
			double lineAY, double lineBX, double lineBY) {

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

			public Point(double d, double e) {
				this.x = d;
				this.y = e;
			}
		}

		public Line(Block r, int ax, int ay, int bx, int by) {

			Point A = rotateAbout(new Point(ax, ay), new Point(r.x + r.width
					/ 2, r.y + r.height / 2), r.angle);
			Point B = rotateAbout(new Point(bx, by), new Point(r.x + r.width
					/ 2, r.y + r.height / 2), r.angle);

			AX = (int) A.x;
			AY = (int) A.y;
			BX = (int) B.x;
			BY = (int) B.y;
			rect = r;
		}

		private Point rotateAbout(Point p, Point c, double theta) {
			double tempX = p.x - c.x;
			double tempY = p.y - c.y;
			double rotatedX = tempX * Math.cos(theta) - tempY * Math.sin(theta);
			double rotatedY = tempX * Math.sin(theta) + tempY * Math.cos(theta);
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

		private int minX = this.x;
		private int maxX = this.x + this.width;
		private int minY = this.y;
		private int maxY = this.y + this.height;

		private Block prev;
		private Block next;
		private double light;
		private double amp;
		private int delay;

		private void destroy() {
			if (this.prev != null)
				this.getPrev().setNext(null);
			if (this.next != null)
				this.getNext().setPrev(null);
			blockList.remove(this);
		}

		private void lightUpPrev(double li, int delay) {
			if (li < 0.05)
				return;
			this.amp = li;
			this.light = 1.0;
			this.delay = delay;
			if (this.light > 1)
				this.light = 1;
			if (this.getPrev() != null)
				this.getPrev().lightUpPrev(li / 1.5, delay + 2);
		}

		private void lightUpNext(double li, int delay) {
			if (li < 0.05)
				return;
			this.amp = li;
			this.light = 1.0;
			this.delay = delay;
			if (this.light > 1)
				this.light = 1;
			if (this.getNext() != null)
				this.getNext().lightUpNext(li / 1.5, delay + 2);
		}

		public void hit() {
			this.light = 1.0;
			this.amp = 1.0;

			if (this.prev != null)
				this.getPrev().lightUpPrev(1.0 / 1.5, 2);
			if (this.next != null)
				this.getNext().lightUpNext(1.0 / 1.5, 2);

			// this.destroy();
			this.hp--;
			if (this.hp <= 0) {
				this.destroy();
			}
			// ball.xsp*=1.1;
			// ball.ysp*=1.1;
		}

		public void update() {

			if (delay > 0) {
				delay--;
			} else {
				if (light > 0) {

					light -= 0.15;
				}
			}

			if (light < 0) {
				light = 0;
			}
		}

		public Block(int x, int y, int width, int height, int hp, double a) {
			super(x, y, width, height, a);
			this.hp = hp;
			this.prev = null;
			this.next = null;
			this.light = 0;
			this.amp = 0;
			this.delay = 0;
			this.lines = new Line[] {
					new Line(this, this.x, this.y, this.x + this.width, this.y),
					new Line(this, this.x + this.width, this.y, this.x
							+ this.width, this.y + this.height),
					new Line(this, this.x + this.width, this.y + this.height,
							this.x, this.y + this.height),
					new Line(this, this.x, this.y + this.height, this.x, this.y) };

			for (Line l : this.lines) {
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

		public Block(int x1, int y1, int x2, int y2, int x3, int y3, int x4,
				int y4, int hp) {
			super(x1, y1, 1, 1, 0);
			this.hp = hp;
			this.prev = null;
			this.next = null;
			this.light = 0;
			this.amp = 0;
			this.delay = 0;
			this.lines = new Line[] { new Line(this, x1, y1, x2, y2),
					new Line(this, x2, y2, x3, y3),
					new Line(this, x3, y3, x4, y4),
					new Line(this, x4, y4, x1, y1) };

			for (Line l : this.lines) {
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

		public Block getNext() {
			return this.next;
		}

		public void setNext(Block next) {
			this.next = next;
		}

		public Block getPrev() {
			return this.prev;
		}

		public void setPrev(Block prev) {
			this.prev = prev;
		}
	}

	private class Paddle extends Rect {
		private int hsp;
		private double light;

		public Paddle(int x, int y, int width, int height) {
			super(x, y, width, height);
			this.hsp = 32;
			this.light = 0;
		}

		private void moveTo(int dx) {
			if (Math.abs(dx - this.x) > this.hsp) {
				this.x += this.hsp * Math.signum((double) (dx - this.x));
			} else {
				this.x = dx;
			}
		}

		public void update() {
			if (light > 0) {
				light -= 0.15;
			}

			if (light < 0) {
				light = 0;
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

		private void transformDirection(double N) {
			double c_speed = dist(0, 0, this.xsp, this.ysp);
			double c_angle = Math.atan2(this.ysp, this.xsp);
			double e_angle = 2 * N - c_angle - Math.PI;

			this.xsp = c_speed * Math.cos(e_angle);
			this.ysp = c_speed * Math.sin(e_angle);

		}

		private void setDirection(double N) {
			double c_speed = dist(0, 0, this.xsp, this.ysp);
			double e_angle = N;

			this.xsp = c_speed * Math.cos(e_angle);
			this.ysp = c_speed * Math.sin(e_angle);
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

		private void bounce(Line l) {
			transformDirection(-Math.PI / 2
					+ Math.atan2(l.BY - l.AY, l.BX - l.AX));
			l.rect.hit();
		}

		private void update() {
			if (Math.abs(ysp) < 1) {
				ysp = Math.signum(ysp);
			}

			if (collisionRectRect(this.x - this.radius, this.y - this.radius,
					this.x + this.radius, this.y + this.radius, paddle.x,
					paddle.y, paddle.x + paddle.width, paddle.y + paddle.height)) {
				paddle.light = 1.0;
				setDirection((-90 + 60
						* (ball.x - (paddle.x + paddle.width / 2))
						/ paddle.width)
						* Math.PI / 180);
			}

			if (this.x + this.radius > screen_width && this.xsp > 0) {
				this.x = screen_width - this.radius;
				this.xsp *= -1;
			} else if (this.x - this.radius < 0 && this.xsp < 0) {
				this.x = this.radius;
				this.xsp *= -1;
			} else if (this.y - this.radius < 0 && this.ysp < 0) {
				this.y = this.radius;
				this.ysp *= -1;
			} else if (this.y + this.radius > screen_height && this.ysp > 0) {
				// game over
				this.ysp *= -1;
			}

			Line tempL = null;
			int mult = (int) dist(0, 0, this.xsp, this.ysp);

			for (int i = 0; i < mult; i++) {
				tempL = checkPlace(xsp / mult, ysp / mult);
				if (tempL != null) {
					bounce(tempL);
				} else {
					x += xsp / mult;
					y += ysp / mult;
				}
			}

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

		Path2D.Double path = new Path2D.Double();
		path.moveTo(100, 100);
		path.quadTo(400, 200, 700, 100);
		path.quadTo(550, 300, 400, 200);
		path.quadTo(250, 100, 100, 300);
		path.quadTo(400, 400, 700, 300);

		// path.quadTo(/*100, 300,*/ 250, 300, 400, 300);

		AffineTransform at = new AffineTransform();
		int cx = 40;
		int cy = 60;

		int pinx1 = 0;
		int piny1 = 0;
		int pinx2 = 0;
		int piny2 = 0;
		Block prev = null;

		FlatteningPathIterator fpi = new FlatteningPathIterator(
				path.getPathIterator(at), 3, 6);
		PathIterator pi = fpi;// path.getPathIterator(at);
		int segnumber = 0;
		while (pi.isDone() == false) {
			segnumber++;
			float[] coords = new float[6];
			pi.currentSegment(coords);
			double angle = (Math.atan2(coords[1] - cy, coords[0] - cx));
			double wid = 8;

			int tx1 = (int) (coords[0] + wid * Math.cos(angle + Math.PI / 2));
			int ty1 = (int) (coords[1] + wid * Math.sin(angle + Math.PI / 2));
			int tx2 = (int) (coords[0] + wid * Math.cos(angle - Math.PI / 2));
			int ty2 = (int) (coords[1] + wid * Math.sin(angle - Math.PI / 2));

			Block block = null;
			if (pinx1 == 0 && piny1 == 0) {
				block = new Block((int) (cx + wid
						* Math.cos(angle + Math.PI / 2)), (int) (cy + wid
						* Math.sin(angle + Math.PI / 2)), tx1, ty1, tx2, ty2,
						(int) (cx + wid * Math.cos(angle - Math.PI / 2)),
						(int) (cy + wid * Math.sin(angle - Math.PI / 2)),
						1 + segnumber / 6);
			} else {
				block = new Block(pinx1, piny1, tx1, ty1, tx2, ty2, pinx2,
						piny2, 1 + segnumber / 6);
			}

			block.setPrev(prev);
			if (block.getPrev() != null) {
				(block.getPrev()).setNext(block);
			}

			blockList.add(block);
			cx = (int) coords[0];
			cy = (int) coords[1];
			pinx1 = tx1;
			piny1 = ty1;
			pinx2 = tx2;
			piny2 = ty2;
			prev = block;
			pi.next();
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
		for (Block b : blockList) {
			b.update();
		}
		paddle.update();
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
			Color c = Color.WHITE;
			for (Block b : blockList) {
				switch (b.hp) {
				case 1:
					c = Color.RED;
					break;
				case 2:
					c = Color.ORANGE;
					break;
				case 3:
					c = Color.YELLOW;
					break;
				case 4:
					c = Color.GREEN;
					break;
				case 5:
					c = Color.BLUE;
					break;
				case 6:
					c = Color.MAGENTA;
					break;
				case 7:
					c = Color.lightGray;
					break;
				default:
					c = Color.WHITE;
					break;
				}

				g2.setColor(blend(c, Color.WHITE,
						b.amp * Math.sin(b.light * Math.PI)));
				/*
				 * Graphics2D gg = (Graphics2D) g2.create();
				 * gg.translate(b.x+b.width/2, b.y+b.height/2);
				 * gg.rotate(b.angle); gg.fillRect(-b.width/2, -b.height/2,
				 * b.width, b.height); gg.dispose();
				 */

				int[] xPoints = { b.lines[0].AX, b.lines[1].AX, b.lines[2].AX,
						b.lines[3].AX };
				int[] yPoints = { b.lines[0].AY, b.lines[1].AY, b.lines[2].AY,
						b.lines[3].AY };
				g2.fillPolygon(xPoints, yPoints, 4);

				g2.setColor(Color.BLACK);
				for (Line l : b.lines) {
					g2.setStroke(new BasicStroke((float) (4 - 4 * b.amp
							* Math.sin(b.light * Math.PI))));
					g2.drawLine(l.AX, l.AY, l.BX, l.BY);
				}
			}
		}

		g2.setColor(blend(Color.GRAY, Color.WHITE,paddle.light));
		g2.fillRect((int)(paddle.x-8*paddle.light),(int)(paddle.y+2*paddle.light),
				(int)(paddle.width+16*paddle.light), (int)(paddle.height-2*paddle.light));
		
		g2.setColor(Color.WHITE);
		g2.fillOval((int) (ball.x - ball.radius), (int) (ball.y - ball.radius),
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
